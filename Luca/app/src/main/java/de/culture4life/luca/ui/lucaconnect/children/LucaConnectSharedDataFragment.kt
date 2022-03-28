package de.culture4life.luca.ui.lucaconnect.children

import android.annotation.SuppressLint
import androidx.viewbinding.ViewBinding
import de.culture4life.luca.databinding.FragmentLucaConnectSharedDataBinding
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowChildFragment
import de.culture4life.luca.ui.lucaconnect.LucaConnectBottomSheetViewModel
import de.culture4life.luca.util.getReadableDate

class LucaConnectSharedDataFragment : BaseFlowChildFragment<LucaConnectSharedDataViewModel, LucaConnectBottomSheetViewModel>() {

    private lateinit var binding: FragmentLucaConnectSharedDataBinding

    override fun getViewModelClass() = LucaConnectSharedDataViewModel::class.java
    override fun getSharedViewModelClass() = LucaConnectBottomSheetViewModel::class.java

    override fun getViewBinding(): ViewBinding {
        binding = FragmentLucaConnectSharedDataBinding.inflate(layoutInflater)
        return binding
    }

    companion object {
        fun newInstance(): LucaConnectSharedDataFragment {
            return LucaConnectSharedDataFragment()
        }
    }

    override fun initializeViews() {
        super.initializeViews()
        initializeObservers()
    }

    private fun initializeObservers() {
        binding.actionButton.setOnClickListener { viewModel.onActionButtonClicked() }
        observe(viewModel.additionalTransferData, ::showData)
    }

    @SuppressLint("SetTextI18n")
    private fun showData(transferData: AdditionalTransferData) {
        binding.apply {
            name.text = "${transferData.firstName} ${transferData.lastName}"
            dateOfBirth.text = requireContext().getReadableDate(transferData.dateOfBirth)
            phone.text = transferData.phoneNumber
            addressLine1.text = "${transferData.street} ${transferData.houseNumber}"
            addressLine2.text = "${transferData.postalCode} ${transferData.city}"
            email.text = transferData.email
        }
    }
}
