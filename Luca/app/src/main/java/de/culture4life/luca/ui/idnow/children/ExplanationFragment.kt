package de.culture4life.luca.ui.idnow.children

import androidx.viewbinding.ViewBinding
import de.culture4life.luca.databinding.FragmentLucaIdExplanationBinding
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowChildFragment
import de.culture4life.luca.ui.idnow.IdNowEnrollFlowViewModel

class ExplanationFragment : BaseFlowChildFragment<ExplanationViewModel, IdNowEnrollFlowViewModel>() {

    private lateinit var binding: FragmentLucaIdExplanationBinding

    override fun getViewModelClass() = ExplanationViewModel::class.java
    override fun getSharedViewModelClass() = IdNowEnrollFlowViewModel::class.java

    override fun getViewBinding(): ViewBinding {
        binding = FragmentLucaIdExplanationBinding.inflate(layoutInflater)
        return binding
    }

    override fun initializeViews() {
        super.initializeViews()
        initializeOnClickListeners()
    }

    private fun initializeOnClickListeners() {
        binding.actionButton.setOnClickListener {
            viewModel.onActionButtonClicked()
        }
    }

    companion object {
        fun newInstance(): ExplanationFragment {
            return ExplanationFragment()
        }
    }
}
