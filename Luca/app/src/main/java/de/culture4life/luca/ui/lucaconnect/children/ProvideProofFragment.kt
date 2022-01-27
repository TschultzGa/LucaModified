package de.culture4life.luca.ui.lucaconnect.children

import androidx.fragment.app.activityViewModels
import androidx.viewbinding.ViewBinding
import com.google.android.material.color.MaterialColors
import de.culture4life.luca.R
import de.culture4life.luca.databinding.FragmentProvideProofBinding
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowChildFragment
import de.culture4life.luca.ui.lucaconnect.LucaConnectBottomSheetViewModel
import de.culture4life.luca.ui.qrcode.AddCertificateFlowFragment
import de.culture4life.luca.ui.qrcode.AddCertificateFlowViewModel

class ProvideProofFragment : BaseFlowChildFragment<ProvideProofViewModel, LucaConnectBottomSheetViewModel>() {

    private val addCertificateViewModel: AddCertificateFlowViewModel by activityViewModels()
    private lateinit var binding: FragmentProvideProofBinding

    override fun getViewModelClass(): Class<ProvideProofViewModel> = ProvideProofViewModel::class.java
    override fun getSharedViewModelClass(): Class<LucaConnectBottomSheetViewModel> = LucaConnectBottomSheetViewModel::class.java

    override fun getViewBinding(): ViewBinding {
        binding = FragmentProvideProofBinding.inflate(layoutInflater)
        return binding
    }

    override fun initializeViews() {
        super.initializeViews()
        initializeObservers()
    }

    private fun initializeObservers() {
        binding.actionButton.setOnClickListener {
            viewModel.onActionButtonClicked()
        }

        observe(viewModel.validProofAvailable) {
            if (!it.hasBeenHandled()) {
                if (it.valueAndMarkAsHandled) {
                    showProofAvailable()
                } else {
                    showNoProofAvailable()
                }
            }
        }

        observe(addCertificateViewModel.onViewDismissedDocumentAdded) {
            if (!it.hasBeenHandled() && it.valueAndMarkAsHandled) {
                viewModel.onCertificateAdded()
            }
        }
    }

    private fun showAddCertificateFlow() {
        AddCertificateFlowFragment.newInstance().show(parentFragmentManager, AddCertificateFlowFragment.TAG)
    }

    private fun showNoProofAvailable() {
        binding.proofAvailableTextView.apply {
            setText(R.string.luca_connect_provide_proof_no_certificate)
            setTextColor(MaterialColors.getColor(this, R.attr.colorWarning))
        }

        binding.actionButton.apply {
            setText(R.string.luca_connect_provide_proof_add_certificate_action)
            setOnClickListener { showAddCertificateFlow() }
        }
    }

    private fun showProofAvailable() {
        binding.proofAvailableTextView.apply {
            setText(R.string.luca_connect_provide_proof_certificate)
            setTextColor(MaterialColors.getColor(this, R.attr.colorOnSurface))
        }

        binding.actionButton.apply {
            setText(R.string.luca_connect_provide_proof_action)
            setOnClickListener { viewModel.onActionButtonClicked() }
        }
    }

    companion object {
        fun newInstance(): ProvideProofFragment {
            return ProvideProofFragment()
        }
    }
}