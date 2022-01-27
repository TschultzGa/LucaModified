package de.culture4life.luca.ui.checkin.flow

import android.widget.TextView
import de.culture4life.luca.R
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowChildFragment
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowChildViewModel
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowViewModel

abstract class BaseCheckInFlowFragment<ViewModelType : BaseFlowChildViewModel, ParentViewModel : BaseFlowViewModel> :
    BaseFlowChildFragment<ViewModelType, ParentViewModel>() {

    override fun initializeViews() {
        super.initializeViews()
        updateActionButtonText()
    }

    protected fun updateActionButtonText() {
        view?.also {
            val actionButton = it.findViewById<TextView>(R.id.actionButton)
            if (viewModel?.isLastPage(this) == true) {
                actionButton.setText(R.string.action_check_in)
            } else {
                actionButton.setText(R.string.action_continue)
            }
        }
    }
}