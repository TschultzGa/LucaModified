package de.culture4life.luca.ui.idnow.children

import androidx.viewbinding.ViewBinding
import de.culture4life.luca.databinding.FragmentLucaIdSuccessBinding
import de.culture4life.luca.idnow.IdNowManager
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowChildFragment
import de.culture4life.luca.ui.idnow.IdNowEnrollFlowViewModel

class SuccessFragment : BaseFlowChildFragment<SuccessViewModel, IdNowEnrollFlowViewModel>() {

    private lateinit var binding: FragmentLucaIdSuccessBinding

    override fun getViewModelClass() = SuccessViewModel::class.java
    override fun getSharedViewModelClass() = IdNowEnrollFlowViewModel::class.java

    override fun getViewBinding(): ViewBinding {
        binding = FragmentLucaIdSuccessBinding.inflate(layoutInflater)
        return binding
    }

    override fun initializeViews() {
        super.initializeViews()
        initializeObservers()
    }

    private fun initializeObservers() {
        binding.actionButton.setOnClickListener {
            startActivity(IdNowManager.createIdNowPlayStoreIntent())
            viewModel.onActionButtonClicked()
        }
    }

    companion object {
        fun newInstance(): SuccessFragment {
            return SuccessFragment()
        }
    }
}
