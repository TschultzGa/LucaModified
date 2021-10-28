package de.culture4life.luca.ui.checkin

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import de.culture4life.luca.R
import de.culture4life.luca.databinding.BottomSheetVoluntaryCheckinBinding

class VoluntaryCheckInBottomSheetFragment : BottomSheetDialogFragment() {

    private lateinit var binding: BottomSheetVoluntaryCheckinBinding
    private val viewModel: VoluntaryCheckInViewModel by activityViewModels()

    private lateinit var locationUrl: String
    private lateinit var locationName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(DialogFragment.STYLE_NORMAL, R.style.BottomSheetStyle)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet =
                dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            with(BottomSheetBehavior.from(bottomSheet!!)) {
                skipCollapsed = true
                state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = BottomSheetVoluntaryCheckinBinding.inflate(inflater)

        arguments?.apply {
            getString(KEY_LOCATION_URL)?.let { locationUrl = it }
            getString(KEY_LOCATION_NAME)?.let { locationName = it }
        }

        initializeView()
        return binding.root
    }

    private fun initializeView() {
        binding.voluntaryCheckInButton.setOnClickListener {
            viewModel.onVoluntaryCheckInButtonPressed(binding.discloseContactDataCheckBox.isChecked, locationUrl)
            dismiss()
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        viewModel.onViewDismissed()
        super.onDismiss(dialog)
    }

    companion object {
        const val TAG = "VoluntaryCheckInBottomSheetFragment"
        const val KEY_LOCATION_URL = "locationUrl"
        const val KEY_LOCATION_NAME = "locationName"

        fun newInstance(): VoluntaryCheckInBottomSheetFragment {
            return VoluntaryCheckInBottomSheetFragment()
        }
    }
}