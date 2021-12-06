package de.culture4life.luca.ui.account

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.viewbinding.ViewBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.culture4life.luca.BuildConfig
import de.culture4life.luca.R
import de.culture4life.luca.databinding.FragmentAccountBinding
import de.culture4life.luca.ui.BaseFragment
import de.culture4life.luca.ui.dialog.BaseDialogFragment
import de.culture4life.luca.ui.registration.RegistrationActivity
import io.reactivex.rxjava3.core.Completable

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

    override fun initializeViews(): Completable {
        return super.initializeViews()
            .andThen {
                initializeOnClickListeners()
            }
    }

    private fun initializeOnClickListeners() {
        binding.editContactDataItem.setOnClickListener {
            val intent = Intent(activity, RegistrationActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }

        binding.guidesItem.setOnClickListener { viewModel.openNewsView() }
        binding.faqItem.setOnClickListener { application.openUrl(getString(R.string.url_faq)) }
        binding.supportItem.setOnClickListener { viewModel.requestSupportMail() }

        binding.dataRequestItem.setOnClickListener { showDataRequestMenu() }
        binding.dataProtectionItem.setOnClickListener { application.openUrl(getString(R.string.url_privacy_policy)) }
        binding.termsItem.setOnClickListener { application.openUrl(getString(R.string.url_terms_and_conditions)) }
        binding.imprintItem.setOnClickListener { application.openUrl(getString(R.string.url_imprint)) }
        binding.dailyKeyItem.setOnClickListener { viewModel.openDailyKeyView() }
        binding.versionItem.setOnClickListener { showVersionDetailsDialog() }
        binding.appDataItem.setOnClickListener { application.openAppSettings() }
        binding.sourceCodeItem.setOnClickListener { showGitlabDialog() }

        binding.deleteAccountItem.setOnClickListener { showDeleteAccountDialog() }
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

    private fun showDeleteAccountDialog() {
        context?.also {
            BaseDialogFragment(MaterialAlertDialogBuilder(it)
                .setTitle(R.string.delete_account_dialog_title)
                .setMessage(R.string.delete_account_dialog_message)
                .setPositiveButton(R.string.delete_account_dialog_action) { _, _ -> viewModel.deleteAccount() }
                .setNegativeButton(R.string.action_cancel) { dialog, _ -> dialog.dismiss() })
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
            BaseDialogFragment(MaterialAlertDialogBuilder(it)
                .setTitle(R.string.version_details_dialog_title)
                .setMessage(message)
                .setPositiveButton(R.string.version_details_dialog_action) { dialog, _ -> dialog.dismiss() })
                .show()
        }
    }

    private fun showGitlabDialog() {
        context?.let {
            BaseDialogFragment(MaterialAlertDialogBuilder(it)
                .setTitle(R.string.gitlab_dialog_title)
                .setMessage(R.string.gitlab_dialog_message)
                .setPositiveButton(R.string.gitlab_dialog_action) { _, _ ->
                    application.openUrl(getString(R.string.url_gitlab))
                }
                .setNegativeButton(R.string.action_cancel) { dialog, _ -> dialog.dismiss() })
                .show()
        }
    }

}