package de.culture4life.luca.ui.myluca

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewbinding.ViewBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.culture4life.luca.R
import de.culture4life.luca.consent.ConsentManager
import de.culture4life.luca.databinding.FragmentMyLucaBinding
import de.culture4life.luca.databinding.LayoutTopSheetBinding
import de.culture4life.luca.document.Document
import de.culture4life.luca.registration.Person
import de.culture4life.luca.ui.BaseQrCodeFragment
import de.culture4life.luca.ui.BaseQrCodeViewModel
import de.culture4life.luca.ui.dialog.BaseDialogFragment
import de.culture4life.luca.ui.myluca.MyLucaListAdapter.MyLucaListClickListener
import de.culture4life.luca.util.addTo
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber

class MyLucaFragment : BaseQrCodeFragment<MyLucaViewModel>(), MyLucaListClickListener {

    private val myLucaListAdapter = MyLucaListAdapter(this, this)
    private lateinit var binding: FragmentMyLucaBinding

    override fun getViewBinding(): ViewBinding {
        binding = FragmentMyLucaBinding.inflate(layoutInflater)
        return binding
    }

    override fun getViewModelClass(): Class<MyLucaViewModel> {
        return MyLucaViewModel::class.java
    }

    override fun initializeViewModel(): Completable {
        return super.initializeViewModel()
            .observeOn(AndroidSchedulers.mainThread())
            .doOnComplete { viewModel.setupViewModelReference(requireActivity()) }
    }

    override fun initializeViews() {
        super.initializeViews()
        initializeMyLucaItemsViews()
        initializeImportViews()
        initializeBanners()
    }

    override fun initializeCameraPreview() {
        super.initializeCameraPreview()
        cameraPreviewView = binding.cameraPreviewView
    }

    private fun initializeMyLucaItemsViews() {
        binding.myLucaRecyclerView.adapter = myLucaListAdapter
        binding.myLucaRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.childrenActionBarMenuImageView.setOnClickListener { viewModel.onChildrenManagementRequested() }
        binding.childrenCounterTextView.setOnClickListener { viewModel.onChildrenManagementRequested() }
        observe(viewModel.children) {
            if (it.isEmpty()) {
                binding.childrenCounterTextView.visibility = View.GONE
            } else {
                binding.childrenCounterTextView.visibility = View.VISIBLE
                binding.childrenCounterTextView.text = it.size.toString()
            }
        }
        observe(viewModel.myLucaItems) {
            val listItems = myLucaListAdapter.setItems(it, getPersons())
            val emptyStateVisibility = if (listItems.isEmpty()) View.VISIBLE else View.GONE
            val contentVisibility = if (listItems.isNotEmpty()) View.VISIBLE else View.GONE
            binding.emptyStateScrollView.visibility = emptyStateVisibility
            binding.myLucaRecyclerView.visibility = contentVisibility
        }
        observe(viewModel.itemToDelete) {
            if (!it.hasBeenHandled()) {
                showDeleteDocumentDialog(it.valueAndMarkAsHandled)
            }
        }
        observe(viewModel.itemToExpand) {
            if (!it.hasBeenHandled()) {
                val wrapper = myLucaListAdapter.getWrapperWith(it.valueAndMarkAsHandled)!!
                for (wrapperItem in wrapper.items) {
                    wrapperItem.toggleExpanded()
                }
                myLucaListAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun initializeImportViews() {
        binding.appointmentsActionBarMenuImageView.setOnClickListener { viewModel.onAppointmentRequested() }
        binding.primaryActionButton.setOnClickListener { toggleCameraPreview() }
        observe(viewModel.isLoading) {
            binding.loadingLayout.visibility = if (it) View.VISIBLE else View.GONE
        }
        observe(viewModel.parsedDocument) {
            if (!it.hasBeenHandled()) {
                showDocumentImportConsentDialog(it.valueAndMarkAsHandled)
            }
        }
        observe(viewModel.addedDocument) {
            if (!it.hasBeenHandled()) {
                it.setHandled(true)
                Toast.makeText(context, R.string.document_import_success_message, Toast.LENGTH_SHORT).show()
            }
        }
        observe(viewModel.possibleCheckInData) {
            if (!it.hasBeenHandled()) {
                showCheckInDialog(it.valueAndMarkAsHandled)
            }
        }
        observe(viewModel.showBirthDateHint) {
            if (!it.hasBeenHandled()) {
                showDocumentImportBirthdayMismatchDialog(it.valueAndMarkAsHandled)
            }
        }
        observe(viewModel.bundle) { processBundle(it) }
    }

    private fun initializeBanners() {
        observe(viewModel.isGenuineTime, ::refreshBanners)
    }

    override fun onResume() {
        super.onResume()
        viewDisposable.add(
            Completable.mergeArray(
                viewModel.updateUserName(),
                viewModel.invokeListUpdate(),
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

    private fun toggleCameraPreview() {
        if (cameraPreviewDisposable == null || cameraPreviewDisposable!!.isDisposed) {
            showCameraPreview(requestConsent = true, requestPermission = true)
        } else {
            hideCameraPreview()
        }
    }

    override fun setCameraPreviewVisible(isVisible: Boolean) {
        super.setCameraPreviewVisible(isVisible)
        val cameraVisibility: Int
        val contentVisibility: Int
        val emptyStateVisibility: Int
        if (isVisible) {
            cameraVisibility = View.VISIBLE
            contentVisibility = View.GONE
            emptyStateVisibility = contentVisibility
            binding.primaryActionButton.setText(R.string.action_cancel)
        } else {
            cameraVisibility = View.GONE
            contentVisibility = View.VISIBLE
            emptyStateVisibility = if (myLucaListAdapter.itemCount == 0) {
                View.VISIBLE
            } else {
                View.GONE
            }
            binding.primaryActionButton.setText(R.string.document_import_action)
        }
        binding.scanDocumentHintTextView.visibility = cameraVisibility
        binding.cardView.visibility = cameraVisibility
        binding.surfaceBackground.visibility = cameraVisibility
        binding.myLucaRecyclerView.visibility = contentVisibility
        binding.bannerScrollView.visibility = contentVisibility
        binding.emptyStateScrollView.visibility = emptyStateVisibility
    }

    private fun showDocumentImportConsentDialog(document: Document) {
        hideCameraPreview()
        val consentManager = application.consentManager
        consentManager.initialize(application)
            .andThen(consentManager.requestConsentAndGetResult(ConsentManager.ID_IMPORT_DOCUMENT))
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { consent, _ ->
                if (consent.approved) {
                    viewModel.addDocumentIfBirthDatesMatch(document)
                        .subscribeOn(Schedulers.io())
                        .subscribe(
                            { Timber.i("Document added: %s", document) },
                            { Timber.w("Unable to add document: %s", it.toString()) }
                        )
                        .addTo(viewDisposable)
                }
            }
    }

    private fun showDocumentImportBirthdayMismatchDialog(document: Document) {
        hideCameraPreview()
        BaseDialogFragment(MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.document_import_error_different_birth_dates_title)
            .setMessage(R.string.document_import_error_different_birth_dates_description)
            .setPositiveButton(R.string.action_ok) { _, _ -> }
            .setNeutralButton(R.string.document_import_anyway_action) { _, _ ->
                viewModel.addDocument(document)
                    .onErrorComplete()
                    .subscribeOn(Schedulers.io())
                    .subscribe()
                    .addTo(viewDisposable)
            })
            .show()
    }

    private fun showCheckInDialog(documentData: String) {
        hideCameraPreview()
        BaseDialogFragment(MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.document_import_check_in_redirect_title)
            .setMessage(R.string.document_import_check_in_redirect_description)
            .setPositiveButton(R.string.action_continue) { _, _ ->
                val bundle = Bundle()
                bundle.putString(BaseQrCodeViewModel.BARCODE_DATA_KEY, documentData)
                safeNavigateFromNavController(R.id.checkInFragment, bundle)
            }
            .setNegativeButton(R.string.action_cancel) { _, _ -> })
            .show()
    }

    private fun showDeleteDocumentDialog(myLucaListItem: MyLucaListItem) {
        BaseDialogFragment(MaterialAlertDialogBuilder(requireContext())
            .setTitle(myLucaListItem.getDeleteButtonText())
            .setMessage(R.string.document_delete_confirmation_message)
            .setPositiveButton(R.string.action_confirm) { _, _ ->
                viewModel.deleteListItem(myLucaListItem)
                    .onErrorComplete()
                    .subscribeOn(Schedulers.io())
                    .subscribe()
                    .addTo(viewDisposable)
            }
            .setNegativeButton(R.string.action_cancel) { _, _ -> })
            .show()
    }

    private fun showDocumentNotVerifiedDialog() {
        BaseDialogFragment(
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.document_not_verified_dialog_title)
                .setMessage(R.string.document_not_verified_dialog_description)
                .setPositiveButton(R.string.action_ok) { dialog, _ -> dialog.dismiss() }
        ).show()
    }

    override fun onDelete(myLucaListItem: MyLucaListItem) {
        showDeleteDocumentDialog(myLucaListItem)
    }

    override fun onIcon(myLucaListItem: MyLucaListItem) {
        if (!myLucaListItem.document.isVerified) {
            showDocumentNotVerifiedDialog()
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