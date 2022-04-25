package de.culture4life.luca.ui.myluca

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelStoreOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewbinding.ViewBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.culture4life.luca.R
import de.culture4life.luca.databinding.FragmentMyLucaBinding
import de.culture4life.luca.databinding.LayoutTopSheetBinding
import de.culture4life.luca.registration.Person
import de.culture4life.luca.ui.BaseFragment
import de.culture4life.luca.ui.BaseQrCodeViewModel
import de.culture4life.luca.ui.ViewError
import de.culture4life.luca.ui.dialog.BaseDialogFragment
import de.culture4life.luca.ui.idnow.UserAuthenticationRequiredPrompt
import de.culture4life.luca.ui.myluca.MyLucaListAdapter.MyLucaListClickListener
import de.culture4life.luca.ui.myluca.listitems.DocumentItem
import de.culture4life.luca.ui.myluca.listitems.IdentityItem
import de.culture4life.luca.ui.myluca.listitems.IdentityRequestedItem
import de.culture4life.luca.ui.myluca.listitems.MyLucaListItem
import de.culture4life.luca.ui.qrcode.AddCertificateFlowFragment
import de.culture4life.luca.ui.recyclerview.LastItemSpacingDecoration
import de.culture4life.luca.util.addTo
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber

class MyLucaFragment : BaseFragment<MyLucaViewModel>(), MyLucaListClickListener {

    private val myLucaListAdapter = MyLucaListAdapter(this, this)
    private lateinit var binding: FragmentMyLucaBinding
    private val userAuthenticationRequiredPrompt = UserAuthenticationRequiredPrompt(this)

    override fun getViewBinding(): ViewBinding {
        binding = FragmentMyLucaBinding.inflate(layoutInflater)
        return binding
    }

    override fun getViewModelClass(): Class<MyLucaViewModel> {
        return MyLucaViewModel::class.java
    }

    override fun getViewModelStoreOwner(): ViewModelStoreOwner {
        return requireActivity()
    }

    override fun initializeViewModel(): Completable {
        return super.initializeViewModel()
            .observeOn(AndroidSchedulers.mainThread())
    }

    override fun initializeViews() {
        super.initializeViews()
        initializeMyLucaItemsViews()
        initializeImportViews()
        initializeBanners()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun initializeMyLucaItemsViews() {
        binding.myLucaRecyclerView.adapter = myLucaListAdapter
        binding.myLucaRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.myLucaRecyclerView.addItemDecoration(LastItemSpacingDecoration(R.dimen.my_luca_recycler_view_padding_bottom))
        binding.childrenCounterTextView.setOnClickListener { viewModel.onChildrenManagementRequested() }
        observe(viewModel.children) {
            if (it.isEmpty()) {
                binding.childrenCounterTextView.text = null
            } else {
                binding.childrenCounterTextView.text = it.size.toString()
            }
        }
        observe(viewModel.myLucaItems) { myLucaListAdapter.setItems(it, getPersons()) }
        observe(viewModel.itemToDelete) {
            if (it.isNotHandled) {
                showDeleteDocumentDialog(it.valueAndMarkAsHandled)
            }
        }
        observe(viewModel.itemToExpand) {
            if (it.isNotHandled) {
                val wrapper = myLucaListAdapter.getWrapperWith(it.valueAndMarkAsHandled)!!
                for (wrapperItem in wrapper.items.filterIsInstance<DocumentItem>()) {
                    wrapperItem.toggleExpanded()
                }
                myLucaListAdapter.notifyItemChanged(myLucaListAdapter.getPositionOfWrapper(wrapper))
            }
        }
    }

    private fun initializeImportViews() {
        binding.appointmentsActionBarMenuImageView.setOnClickListener { viewModel.onAppointmentRequested() }
        binding.primaryActionButton.setOnClickListener { showAddDocument() }
        observe(viewModel.isLoading) {
            binding.loadingIndicator.isVisible = it
        }
        observe(viewModel.addedDocument) {
            if (it.isNotHandled) {
                it.isHandled = true
                Toast.makeText(context, R.string.document_import_success_message, Toast.LENGTH_SHORT).show()
            }
        }
        observe(viewModel.possibleCheckInData) {
            if (it.isNotHandled) {
                showCheckInDialog(it.valueAndMarkAsHandled)
            }
        }
        observe(viewModel.bundleLiveData) { processBundle(it) }
    }

    private fun initializeBanners() {
        observe(viewModel.isGenuineTime, ::refreshBanners)
    }

    override fun onResume() {
        super.onResume()
        viewDisposable.add(
            Completable.mergeArray(
                viewModel.updateUserName(),
                viewModel.invokeServerTimeOffsetUpdate()
            ).subscribe()
        )
        arguments?.let { bundle -> viewModel.setBundle(bundle) }
    }

    override fun onStop() {
        clearBundle()
        super.onStop()
    }

    private fun processBundle(bundle: Bundle?) {
        if (bundle == null) {
            return
        }
        bundle.getString(BaseQrCodeViewModel.BARCODE_DATA_KEY)?.let { barcode ->
            viewModel.process(barcode)
                .doOnComplete { clearBundle() }
                .onErrorComplete()
                .subscribe()
                .addTo(viewDisposable)
        }
    }

    private fun refreshBanners(isGenuineTime: Boolean) {
        val container = binding.bannerLayout
        container.removeAllViews()
        if (!isGenuineTime) {
            val bannerBinding = LayoutTopSheetBinding.inflate(layoutInflater, container, true)
            bannerBinding.sheetDescriptionTextView.setText(R.string.time_error_description)
            bannerBinding.sheetActionButton.setText(R.string.time_error_action)
            bannerBinding.sheetActionButton.setOnClickListener {
                val intent = Intent(Settings.ACTION_DATE_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            }
        }
    }

    private fun showAddDocument() {
        AddCertificateFlowFragment.newInstance().show(childFragmentManager, AddCertificateFlowFragment.TAG)
    }

    private fun showCheckInDialog(documentData: String) {
        BaseDialogFragment(
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.document_import_check_in_redirect_title)
                .setMessage(R.string.document_import_check_in_redirect_description)
                .setPositiveButton(R.string.action_continue) { _, _ ->
                    val bundle = Bundle()
                    bundle.putString(BaseQrCodeViewModel.BARCODE_DATA_KEY, documentData)
                    safeNavigateFromNavController(R.id.action_myLucaFragment_to_checkInFragment, bundle)
                }
                .setNegativeButton(R.string.action_cancel) { _, _ -> }
        )
            .show()
    }

    private fun showDeleteDocumentDialog(documentItem: DocumentItem) {
        BaseDialogFragment(
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(documentItem.deleteButtonText)
                .setMessage(R.string.document_delete_confirmation_message)
                .setPositiveButton(R.string.action_confirm) { _, _ ->
                    viewModel.deleteDocumentListItem(documentItem)
                        .onErrorComplete()
                        .subscribeOn(Schedulers.io())
                        .subscribe()
                        .addTo(viewDisposable)
                }
                .setNegativeButton(R.string.action_cancel) { _, _ -> }
        )
            .show()
    }

    private fun showDeleteIdentityDialog(listItem: MyLucaListItem) {
        BaseDialogFragment(
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.luca_id_deletion_dialog_title)
                .setMessage(R.string.luca_id_deletion_dialog_description)
                .setPositiveButton(R.string.action_confirm) { _, _ -> viewModel.onDeleteIdentityListItem(listItem) }
                .setNegativeButton(R.string.action_cancel) { _, _ -> }
        )
            .show()
    }

    private fun showDocumentNotVerifiedDialog() {
        BaseDialogFragment(
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.certificate_not_verified_dialog_title)
                .setMessage(R.string.certificate_not_verified_dialog_description)
                .setPositiveButton(R.string.action_ok) { dialog, _ -> dialog.dismiss() }
        ).show()
    }

    override fun onDelete(myLucaListItem: MyLucaListItem) {
        when (myLucaListItem) {
            is DocumentItem -> showDeleteDocumentDialog(myLucaListItem)
            is IdentityItem -> showDeleteIdentityDialog(myLucaListItem)
            is IdentityRequestedItem -> showDeleteIdentityDialog(myLucaListItem)
        }
    }

    override fun onExpandIdentity(identityItem: IdentityItem, position: Int) {
        if (identityItem.isExpanded) {
            identityItem.isExpanded = false
            identityItem.idData = null
            myLucaListAdapter.notifyItemChanged(position)
        } else {
            userAuthenticationRequiredPrompt.showForLucaIdDisplay(
                {
                    viewModel.idDataIfAvailable
                        .toSingle()
                        .subscribe(
                            { identity ->
                                Timber.d("Setting ID data: $identity")
                                identityItem.isExpanded = true
                                identityItem.idData = identity
                                myLucaListAdapter.notifyItemChanged(position)
                            },
                            { throwable ->
                                Timber.d("Unable to set ID data: $throwable")
                                val error = ViewError.Builder(requireContext())
                                    .withCause(throwable)
                                    .removeWhenShown()
                                    .build()
                                viewModel.addError(error)
                            }
                        )
                        .addTo(viewDisposable)
                }
            )
        }
    }

    override fun onExpandDocument(documentItem: DocumentItem, position: Int) {
        documentItem.toggleExpanded()
        myLucaListAdapter.notifyItemChanged(position)
    }

    override fun onIcon(myLucaListItem: MyLucaListItem) {
        if (myLucaListItem is DocumentItem) {
            if (!myLucaListItem.document.isVerified) {
                showDocumentNotVerifiedDialog()
            }
        } else if (myLucaListItem is IdentityItem) {
            Toast.makeText(context, R.string.luca_id_card_icon_tooltip, Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearBundle() {
        arguments?.clear()
        viewModel.setBundle(null)
    }

    private fun getPersons(): ArrayList<Person> {
        val persons = ArrayList<Person>()
        persons.add(viewModel.user.value!!)
        persons.addAll(viewModel.children.value!!)
        return persons
    }
}
