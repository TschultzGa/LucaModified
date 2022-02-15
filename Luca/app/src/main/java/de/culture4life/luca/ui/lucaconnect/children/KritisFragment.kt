package de.culture4life.luca.ui.lucaconnect.children

import android.text.method.LinkMovementMethod
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.LiveData
import androidx.transition.TransitionManager
import androidx.viewbinding.ViewBinding
import com.google.android.material.textfield.TextInputLayout
import de.culture4life.luca.R
import de.culture4life.luca.databinding.FragmentKritisBinding
import de.culture4life.luca.registration.ConnectKritisData
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowChildFragment
import de.culture4life.luca.ui.lucaconnect.LucaConnectBottomSheetViewModel

class KritisFragment : BaseFlowChildFragment<KritisViewModel, LucaConnectBottomSheetViewModel>() {

    private lateinit var binding: FragmentKritisBinding

    override fun getViewModelClass(): Class<KritisViewModel> = KritisViewModel::class.java
    override fun getSharedViewModelClass(): Class<LucaConnectBottomSheetViewModel> = LucaConnectBottomSheetViewModel::class.java

    override fun getViewBinding(): ViewBinding {
        binding = FragmentKritisBinding.inflate(layoutInflater)
        return binding
    }

    override fun initializeViews() {
        super.initializeViews()
        initializeOnClickListeners()
        initializeTextChangedListeners()
        initializeObservers()

        binding.kritisDescriptionTextView.movementMethod = LinkMovementMethod.getInstance();
    }

    private fun initializeOnClickListeners() {
        binding.actionButton.setOnClickListener {
            viewModel.onActionButtonClicked(
                if (binding.criticalCheckBox.isChecked) true else null,
                if (binding.vulnerableCheckBox.isChecked) true else null,
                binding.industryTextInputEditText.text.toString(),
                binding.companyTextInputEditText.text.toString()
            )
        }

        binding.criticalCheckBox.setOnClickListener { setInputTextVisible() }
        binding.vulnerableCheckBox.setOnClickListener { setInputTextVisible() }
    }

    private fun initializeTextChangedListeners() {
        binding.industryTextInputLayout.editText?.addTextChangedListener {
            viewModel.validateIndustryInput(it.toString())
        }

        binding.companyTextInputLayout.editText?.addTextChangedListener {
            viewModel.validateCompanyInput(it.toString())
        }
    }

    private fun initializeObservers() {
        observe(viewModel.industryInputError) {
            if (!it) {
                binding.industryTextInputLayout.error = getString(R.string.luca_connect_kritis_input_error)
            } else {
                binding.industryTextInputLayout.error = null
            }
        }

        observe(viewModel.companyInputError) {
            if (!it) {
                binding.companyTextInputLayout.error = getString(R.string.luca_connect_kritis_input_error)
            } else {
                binding.companyTextInputLayout.error = null
            }
        }

        observe(viewModel.hasErrors) { binding.actionButton.isEnabled = !it }
    }

    private fun setInputTextVisible() {
        (parentFragment as DialogFragment).dialog?.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.apply {
            TransitionManager.beginDelayedTransition(this)
        }

        val isVisible = binding.criticalCheckBox.isChecked || binding.vulnerableCheckBox.isChecked
        binding.industryTextInputLayout.isVisible = isVisible
        binding.companyTextInputLayout.isVisible = isVisible
    }

    companion object {
        fun newInstance(): KritisFragment {
            return KritisFragment()
        }
    }
}