package de.culture4life.luca.ui.checkin.flow

import androidx.fragment.app.Fragment
import de.culture4life.luca.network.pojo.LocationResponseData
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowBottomSheetDialogFragment
import de.culture4life.luca.ui.checkin.flow.children.ConfirmCheckInFragment
import de.culture4life.luca.ui.checkin.flow.children.EntryPolicyFragment
import de.culture4life.luca.ui.checkin.flow.children.VoluntaryCheckInFragment
import io.reactivex.rxjava3.core.Completable

class CheckInFlowBottomSheetFragment : BaseFlowBottomSheetDialogFragment<CheckInFlowPage, CheckInFlowViewModel>() {

    override fun getViewModelClass(): Class<CheckInFlowViewModel> = CheckInFlowViewModel::class.java
    override fun lastPageHasBackButton(): Boolean = true

    override fun initializeViewModel(): Completable {
        arguments?.apply {
            viewModel.locationResponseData = arguments?.getSerializable(KEY_LOCATION_RESPONSE_DATA)!! as LocationResponseData
            viewModel.url = arguments?.getString(KEY_LOCATION_URL)
        }
        return super.initializeViewModel()
    }

    override fun initializeViews() {
        super.initializeViews()
        initializeObservers()
    }

    private fun initializeObservers() {
        viewModel.onCheckInRequested.observe(viewLifecycleOwner) {
            if (it.isNotHandled) {
                it.isHandled = true
                dismiss()
            }
        }
    }

    override fun mapPageToFragment(page: CheckInFlowPage): Fragment {
        return when (page) {
            is CheckInFlowPage.ConfirmCheckInPage -> ConfirmCheckInFragment.newInstance(page.arguments)
            is CheckInFlowPage.VoluntaryCheckInPage -> VoluntaryCheckInFragment.newInstance()
            is CheckInFlowPage.EntryPolicyPage -> EntryPolicyFragment.newInstance()
        }
    }

    companion object {
        const val TAG = "CheckInMultiConfirmBottomSheetFragment"
        const val KEY_LOCATION_RESPONSE_DATA = "locationResponseData"
        const val KEY_LOCATION_URL = "locationUrl"

        fun newInstance(): CheckInFlowBottomSheetFragment = CheckInFlowBottomSheetFragment()
    }
}
