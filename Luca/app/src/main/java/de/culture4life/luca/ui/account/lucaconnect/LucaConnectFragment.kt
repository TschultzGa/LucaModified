package de.culture4life.luca.ui.account.lucaconnect

import androidx.lifecycle.ViewModelProvider
import androidx.viewbinding.ViewBinding
import de.culture4life.luca.databinding.FragmentLucaConnectBinding
import de.culture4life.luca.ui.BaseFragment
import de.culture4life.luca.ui.lucaconnect.LucaConnectBottomSheetDialogFragment
import de.culture4life.luca.ui.lucaconnect.LucaConnectBottomSheetViewModel
import de.culture4life.luca.util.setCheckedImmediately
import io.reactivex.rxjava3.core.Completable

class LucaConnectFragment : BaseFragment<LucaConnectViewModel>() {

    private lateinit var binding: FragmentLucaConnectBinding
    private lateinit var lucaConnectBottomSheetViewModel: LucaConnectBottomSheetViewModel
    private val lucaConnectBottomSheetFragment by lazy { LucaConnectBottomSheetDialogFragment.newInstance() }

    override fun getViewBinding(): ViewBinding {
        binding = FragmentLucaConnectBinding.inflate(layoutInflater)
        return binding
    }

    override fun getViewModelClass(): Class<LucaConnectViewModel> {
        return LucaConnectViewModel::class.java
    }

    override fun initializeViewModel(): Completable {
        return super.initializeViewModel()
            .andThen(
                Completable.fromAction {
                    lucaConnectBottomSheetViewModel = ViewModelProvider(requireActivity())
                        .get(LucaConnectBottomSheetViewModel::class.java)
                }
            )
    }

    override fun initializeViews() {
        super.initializeViews()
        binding.lucaConnectToggle.setOnClickListener { viewModel.toggleLucaConnect(binding.lucaConnectToggle.isChecked) }
        initializeObservers()
    }

    private fun initializeObservers() {
        binding.lucaConnectToggle.setCheckedImmediately(viewModel.enrollmentStatus.value)
        observe(viewModel.enrollmentStatus) { binding.lucaConnectToggle.isChecked = it }
        observe(viewModel.openEnrollmentFlow) {
            if (!it.hasBeenHandled() && it.valueAndMarkAsHandled) {
                openEnrollmentFlow()
            }
        }
        observe(lucaConnectBottomSheetViewModel.bottomSheetDismissed) {
            // synchronize with life data again
            binding.lucaConnectToggle.isChecked = viewModel.enrollmentStatus.value == true
        }
    }

    private fun openEnrollmentFlow() {
        lucaConnectBottomSheetFragment.show(parentFragmentManager, LucaConnectBottomSheetDialogFragment.TAG)
    }
}
