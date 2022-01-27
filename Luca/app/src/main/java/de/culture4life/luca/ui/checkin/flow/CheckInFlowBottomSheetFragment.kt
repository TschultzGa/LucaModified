package de.culture4life.luca.ui.checkin.flow

import de.culture4life.luca.network.pojo.LocationResponseData
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowBottomSheetDialogFragment

class CheckInFlowBottomSheetFragment : BaseFlowBottomSheetDialogFragment<CheckInFlowViewModel>() {

    override fun getViewModelClass(): Class<CheckInFlowViewModel> = CheckInFlowViewModel::class.java
    override fun lastPageHasBackButton(): Boolean = true

    override fun initializeViews() {
        super.initializeViews()
        arguments?.apply {
            viewModel.locationResponseData = arguments?.getSerializable(KEY_LOCATION_RESPONSE_DATA)!! as LocationResponseData
            viewModel.url = arguments?.getString(KEY_LOCATION_URL)
        }
        initializeObservers()
        viewModel.initializeViewModel()
    }

    private fun initializeObservers() {
        viewModel.onCheckInRequested.observe(viewLifecycleOwner) {
            if (!it.hasBeenHandled()) {
                it.setHandled(true)
                dismiss()
            }
        }
    }

    companion object {
        const val TAG = "CheckInMultiConfirmBottomSheetFragment"
        const val KEY_LOCATION_RESPONSE_DATA = "locationResponseData"
        const val KEY_LOCATION_URL = "locationUrl"

        fun newInstance(): CheckInFlowBottomSheetFragment {
            return CheckInFlowBottomSheetFragment()
        }
    }
}