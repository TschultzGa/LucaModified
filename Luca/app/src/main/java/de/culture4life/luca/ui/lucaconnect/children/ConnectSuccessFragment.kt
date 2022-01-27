package de.culture4life.luca.ui.lucaconnect.children

import androidx.viewbinding.ViewBinding
import de.culture4life.luca.databinding.FragmentConnectSuccessBinding
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowChildFragment
import de.culture4life.luca.ui.lucaconnect.LucaConnectBottomSheetViewModel

class ConnectSuccessFragment : BaseFlowChildFragment<ConnectSuccessViewModel, LucaConnectBottomSheetViewModel>() {

    private lateinit var binding: FragmentConnectSuccessBinding

    override fun getViewModelClass(): Class<ConnectSuccessViewModel> = ConnectSuccessViewModel::class.java
    override fun getSharedViewModelClass(): Class<LucaConnectBottomSheetViewModel> = LucaConnectBottomSheetViewModel::class.java

    override fun getViewBinding(): ViewBinding {
        binding = FragmentConnectSuccessBinding.inflate(layoutInflater)
        return binding
    }

    override fun initializeViews() {
        super.initializeViews()
        initializeObservers()
    }

    private fun initializeObservers() {
        binding.actionButton.setOnClickListener { viewModel.onActionButtonClicked() }
    }

    companion object {
        fun newInstance(): ConnectSuccessFragment {
            return ConnectSuccessFragment()
        }
    }
}