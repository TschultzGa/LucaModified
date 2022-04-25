package de.culture4life.luca.ui.idnow

import androidx.fragment.app.Fragment
import de.culture4life.luca.ui.base.bottomsheetflow.BaseFlowBottomSheetDialogFragment
import de.culture4life.luca.ui.idnow.children.ConsentFragment
import de.culture4life.luca.ui.idnow.children.ExplanationFragment
import de.culture4life.luca.ui.idnow.children.SuccessFragment

class IdNowEnrollFlowFragment : BaseFlowBottomSheetDialogFragment<LucaIdEnrollmentFlowPage, IdNowEnrollFlowViewModel>() {

    override fun getViewModelClass() = IdNowEnrollFlowViewModel::class.java
    override fun lastPageHasBackButton() = false

    override fun mapPageToFragment(page: LucaIdEnrollmentFlowPage): Fragment {
        return when (page) {
            is LucaIdEnrollmentFlowPage.ExplanationPage -> ExplanationFragment.newInstance()
            is LucaIdEnrollmentFlowPage.ConsentPage -> ConsentFragment.newInstance()
            is LucaIdEnrollmentFlowPage.SuccessPage -> SuccessFragment.newInstance()
        }
    }

    companion object {
        const val TAG = "IdNowEnrollFlowFragment"
        fun newInstance() = IdNowEnrollFlowFragment()
    }
}
