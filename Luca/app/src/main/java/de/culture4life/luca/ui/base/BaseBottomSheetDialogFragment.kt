package de.culture4life.luca.ui.base

import android.content.DialogInterface
import android.content.res.Resources
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.CallSuper
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.viewbinding.ViewBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.culture4life.luca.R
import de.culture4life.luca.ui.ViewError
import de.culture4life.luca.ui.ViewEvent
import de.culture4life.luca.ui.dialog.BaseDialogContent
import de.culture4life.luca.ui.dialog.BaseDialogFragment
import de.culture4life.luca.util.addTo
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import timber.log.Timber

abstract class BaseBottomSheetDialogFragment<ViewModelType : BaseBottomSheetViewModel> : BottomSheetDialogFragment() {

    val viewDisposable = CompositeDisposable()

    protected val viewModel: ViewModelType by lazy { ViewModelProvider(requireActivity())[getViewModelClass()] }

    protected var initialized = false
    protected open var fixedHeight = false

    abstract fun getViewBinding(): ViewBinding
    abstract fun getViewModelClass(): Class<ViewModelType>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(DialogFragment.STYLE_NORMAL, R.style.ThemeOverlay_Luca_BottomSheet)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = getViewBinding()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViewModel()
            .observeOn(AndroidSchedulers.mainThread())
            .doOnComplete {
                initializeViews()
                initialized = true
            }
            .subscribe(
                { Timber.d("Initialized %s with %s", this, viewModel) },
                { Timber.e(it, "Unable to initialize %s with %s: %s", this, viewModel, it.toString()) }
            )
    }

    override fun onStart() {
        super.onStart()

        dialog?.also {
            it.window?.apply {
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setDimAmount(0.8F)
            }

            val bottomSheet = it.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            with(BottomSheetBehavior.from(bottomSheet)) {
                skipCollapsed = true
                state = BottomSheetBehavior.STATE_EXPANDED
            }
            if (fixedHeight) {
                bottomSheet.layoutParams.height = (Resources.getSystem().displayMetrics.heightPixels.toDouble() * 0.9).toInt()
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        viewDisposable.dispose()
        viewModel.onBottomSheetDismissed()
    }

    @CallSuper
    protected open fun initializeViewModel(): Completable {
        return viewModel.initialize()
            .andThen(viewModel.processArguments(arguments))
    }

    @CallSuper
    protected open fun initializeViews() {
        observeOnDismissBottomSheet()
        observeErrors()
        observeDialogRequests()
    }

    protected open fun observeErrors() {
        viewModel.errors.observe(viewLifecycleOwner) {
            if (it.isNotEmpty()) {
                indicateErrors(it)
            }
        }
    }

    protected open fun indicateErrors(errors: Set<ViewError?>) {
        Timber.d("indicateErrors() called with: errors = [%s]", errors)
        for (error in errors) {
            showErrorAsDialog(error!!)
        }
    }

    protected open fun showErrorAsDialog(error: ViewError) {
        if (view == null) {
            return
        }
        val builder = MaterialAlertDialogBuilder(requireActivity())
            .setTitle(error.title)
            .setMessage(error.description)

        if (error.isResolvable) {
            builder.setPositiveButton(
                error.resolveLabel
            ) { _, _ ->
                error.resolveAction!!
                    .subscribe(
                        { Timber.d("Error resolved") },
                        { throwable: Throwable ->
                            Timber.w("Unable to resolve error: %s", throwable.toString())
                        }
                    )
                    .addTo(viewDisposable)
            }
        } else {
            builder.setPositiveButton(R.string.action_ok) { _, _ -> }
        }
        val dialogFragment = BaseDialogFragment(builder)
        dialogFragment.onDismissListener = DialogInterface.OnDismissListener {
            viewModel.onErrorDismissed(
                error
            )
        }
        dialogFragment.show()
        viewModel.onErrorShown(error)
    }

    private fun observeOnDismissBottomSheet() {
        viewModel.dismissBottomSheetRequests.observe(viewLifecycleOwner) {
            if (!it.hasBeenHandled()) {
                if (it.valueAndMarkAsHandled) {
                    dismiss()
                }
            }
        }
    }

    protected open fun observeDialogRequests() {
        viewModel.dialogRequestViewEvent.observe(viewLifecycleOwner) { dialogRequest: ViewEvent<BaseDialogContent?> ->
            if (!dialogRequest.hasBeenHandled()) {
                BaseDialogFragment(requireContext(), dialogRequest.valueAndMarkAsHandled).show()
            }
        }
    }
}
