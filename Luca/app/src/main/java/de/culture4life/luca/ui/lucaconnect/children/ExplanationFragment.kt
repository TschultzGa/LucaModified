package de.culture4life.luca.ui.lucaconnect.children

import androidx.viewbinding.ViewBinding
import de.culture4life.luca.databinding.FragmentExplanationBinding
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowChildFragment
import de.culture4life.luca.ui.lucaconnect.LucaConnectBottomSheetViewModel

class ExplanationFragment : BaseFlowChildFragment<ExplanationViewModel, LucaConnectBottomSheetViewModel>() {

    private lateinit var binding: FragmentExplanationBinding

    override fun getViewModelClass(): Class<ExplanationViewModel> = ExplanationViewModel::class.java
    override fun getSharedViewModelClass(): Class<LucaConnectBottomSheetViewModel> = LucaConnectBottomSheetViewModel::class.java

    override fun getViewBinding(): ViewBinding {
        binding = FragmentExplanationBinding.inflate(layoutInflater)
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
        fun newInstance(): ExplanationFragment {
            return ExplanationFragment()
        }
    }
}