package de.culture4life.luca.ui.account

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.descendants
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mikepenz.aboutlibraries.LibsBuilder
import com.mikepenz.aboutlibraries.LibsConfiguration
import com.mikepenz.aboutlibraries.ui.item.HeaderItem
import de.culture4life.luca.BuildConfig
import de.culture4life.luca.R
import de.culture4life.luca.databinding.FragmentAccountBinding
import de.culture4life.luca.ui.BaseFragment
import de.culture4life.luca.ui.dialog.BaseDialogFragment
import de.culture4life.luca.ui.registration.RegistrationActivity

class AccountFragment : BaseFragment<AccountViewModel>() {

    override fun getViewBinding(): ViewBinding {
        binding = FragmentAccountBinding.inflate(layoutInflater)
        return binding
    }

    override fun getViewModelClass(): Class<AccountViewModel> = AccountViewModel::class.java

    private lateinit var binding: FragmentAccountBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        binding = FragmentAccountBinding.bind(view)
        return binding.root
    }

    override fun initializeViews() {
        super.initializeViews()
        initializeOnClickListeners()
        initializeObservers()
    }

    private fun initializeOnClickListeners() {
        binding.editContactDataItem.setOnClickListener {
            val intent = Intent(activity, RegistrationActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
        binding.postalCodeItem.setOnClickListener { viewModel.openPostalCodeView() }
        binding.lucaConnectItem.setOnClickListener { viewModel.openLucaConnectView() }
        binding.guidesItem.setOnClickListener { viewModel.openNewsView() }
        binding.faqItem.setOnClickListener { application.openUrl(getString(R.string.url_faq)) }
        binding.supportItem.setOnClickListener { viewModel.requestSupportMail() }
        binding.dataRequestItem.setOnClickListener { showDataRequestMenu() }
        binding.dataProtectionItem.setOnClickListener { application.openUrl(getString(R.string.url_privacy_policy)) }
        binding.termsItem.setOnClickListener { application.openUrl(getString(R.string.url_terms_and_conditions)) }
        binding.imprintItem.setOnClickListener { application.openUrl(getString(R.string.url_imprint)) }
        binding.licensesItem.setOnClickListener { onLicenseMenuItemClick() }
        binding.dailyKeyItem.setOnClickListener { viewModel.openDailyKeyView() }
        binding.versionItem.setOnClickListener { showVersionDetailsDialog() }
        binding.appDataItem.setOnClickListener { application.openAppSettings() }
        binding.sourceCodeItem.setOnClickListener { showGitlabDialog() }
        binding.deleteAccountItem.setOnClickListener { showDeleteAccountDialog() }
        binding.directCheckInItem.setOnClickListener { viewModel.openDirectCheckInView() }
        binding.entryPolicyItem.setOnClickListener { viewModel.openEntryPolicyView() }
        binding.voluntaryCheckInItem.setOnClickListener { viewModel.openVoluntaryCheckInView() }
    }

    private fun initializeObservers() {
        observe(viewModel.connectEnrollmentSupportedStatus) {
            binding.lucaConnectItem.visibility = if (it) View.VISIBLE else View.GONE
            binding.postalCodeItem.showSeparator(it)
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.tracingDataRequestMenuItem -> viewModel.exportTracingDataRequest(
                getFileExportUri("luca-tracing-data.txt")
            )
            R.id.documentsDataRequestMenuItem -> viewModel.exportDocumentsDataRequest(
                getFileExportUri("luca-documents-data.txt")
            )
            else -> return super.onMenuItemClick(item)
        }
        return true
    }

    private fun onLicenseMenuItemClick() {
        LibsBuilder()
            .withSearchEnabled(true)
            .withActivityTitle(getString(R.string.account_tab_item_licenses))
            .withLicenseShown(true)
            .withLibsRecyclerViewListener(object : LibsConfiguration.LibsRecyclerViewListener {
                val typeface = ResourcesCompat.getFont(requireContext(), R.font.montserrat_regular)

                // Change fontFamily for all license list items
                override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder) {
                    (viewHolder.itemView as? ViewGroup ?: return)
                        .descendants
                        .filterIsInstance<TextView>()
                        .forEach { it.typeface = typeface }
                }

                override fun onBindViewHolder(headerViewHolder: HeaderItem.ViewHolder) {}
            })
            .start(requireContext())
    }

    private fun showDeleteAccountDialog() {
        context?.also {
            BaseDialogFragment(
                MaterialAlertDialogBuilder(it)
                    .setTitle(R.string.delete_account_dialog_title)
                    .setMessage(R.string.delete_account_dialog_message)
                    .setPositiveButton(R.string.delete_account_dialog_action) { _, _ -> viewModel.deleteAccount() }
                    .setNegativeButton(R.string.action_cancel) { dialog, _ -> dialog.dismiss() }
            )
                .show()
        }
    }

    private fun showDataRequestMenu() {
        context?.also { context ->
            PopupMenu(context, binding.dataRequestItem).apply {
                menuInflater.inflate(R.menu.data_request_menu, this.menu)
                setOnMenuItemClickListener { item -> onMenuItemClick(item) }
                show()
            }
        }
    }

    private fun showVersionDetailsDialog() {
        context?.also {
            var commitHash = BuildConfig.COMMIT_HASH
            if (commitHash.length > 8 && !commitHash.startsWith("<")) {
                commitHash = commitHash.substring(0, 8)
            }
            val message = getString(
                R.string.version_details_dialog_message,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
                commitHash
            )
            BaseDialogFragment(
                MaterialAlertDialogBuilder(it)
                    .setTitle(R.string.version_details_dialog_title)
                    .setMessage(message)
                    .setPositiveButton(R.string.version_details_dialog_action) { dialog, _ -> dialog.dismiss() }
            )
                .show()
        }
    }

    private fun showGitlabDialog() {
        context?.let {
            BaseDialogFragment(
                MaterialAlertDialogBuilder(it)
                    .setTitle(R.string.gitlab_dialog_title)
                    .setMessage(R.string.gitlab_dialog_message)
                    .setPositiveButton(R.string.gitlab_dialog_action) { _, _ ->
                        application.openUrl(getString(R.string.url_gitlab))
                    }
                    .setNegativeButton(R.string.action_cancel) { dialog, _ -> dialog.dismiss() }
            )
                .show()
        }
    }
}
