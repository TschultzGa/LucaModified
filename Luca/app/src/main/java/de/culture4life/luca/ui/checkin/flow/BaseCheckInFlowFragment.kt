package de.culture4life.luca.ui.checkin.flow

import android.widget.TextView
import de.culture4life.luca.R
import de.culture4life.luca.ui.base.bottomsheetflow.*

abstract class BaseCheckInFlowFragment<ViewModelType : BaseFlowChildViewModel, ParentViewModel : BaseFlowViewModel> :
    BaseFlowChildFragment<ViewModelType, ParentViewModel>() {

    override fun onResume() {
        super.onResume()
        updateActionButtonText()
    }

    private fun updateActionButtonText() {
        view?.also {
            val actionButton = it.findViewById<TextView>(R.id.actionButton)
            if ((parentFragment as BaseFlowBottomSheetDialogFragment<*, *>).showsLastPage()) {
                actionButton.setText(R.string.action_check_in)
            } else {
                actionButton.setText(R.string.action_continue)
            }
        }
    }
}
