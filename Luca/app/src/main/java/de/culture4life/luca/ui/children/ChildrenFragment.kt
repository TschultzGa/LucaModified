package de.culture4life.luca.ui.children

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.viewbinding.ViewBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.culture4life.luca.R
import de.culture4life.luca.children.Child
import de.culture4life.luca.databinding.FragmentAddingChildrenBinding
import de.culture4life.luca.ui.BaseFragment
import de.culture4life.luca.ui.UiUtil
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers

class ChildrenFragment : BaseFragment<ChildrenViewModel>(),
    AddChildDialogFragment.AddChildListener {

    private var bottomSheet: AddChildDialogFragment? = null
    private lateinit var binding: FragmentAddingChildrenBinding
    private lateinit var childListAdapter: ChildListAdapter
    private var isCheckedIn = false

    override fun getViewModelClass(): Class<ChildrenViewModel> = ChildrenViewModel::class.java

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            isCheckedIn = it.getBoolean(CHECK_IN, false)
        }
    }

    override fun getViewBinding(): ViewBinding {
        binding = FragmentAddingChildrenBinding.inflate(layoutInflater)
        return binding
    }

    override fun initializeViews(): Completable {
        return super.initializeViews()
            .andThen(Completable.fromAction {
                initializeChildItemsViews()
                initializeAddChildViews()
            })
    }

    override fun onResume() {
        super.onResume()
        viewModel.restoreChildren().subscribe()
    }

    override fun addChild(child: Child) {
        viewDisposable.add(
            viewModel.addChild(child, isCheckedIn)
                .onErrorComplete()
                .doFinally { bottomSheet?.dismiss() }
                .subscribe())
    }

    private fun initializeAddChildViews() {
        with(binding) {
            actionBarBackButtonImageView.setOnClickListener { viewModel.navigateBack() }
            primaryActionButton.setOnClickListener { showAddChildDialog() }
        }
    }

    private fun initializeChildItemsViews() {
        childListAdapter = ChildListAdapter(
            requireContext(),
            binding.childListView.id,
            viewModel,
            isCheckedIn,
            { showRemoveChildDialog(it) },
            { showAddChildDialog() }
        )
        binding.childListView.adapter = childListAdapter
        View(context).let { paddingView ->
            paddingView.minimumHeight = UiUtil.convertDpToPixel(16f, context).toInt()
            binding.childListView.addHeaderView(paddingView)
        }
        observe(viewModel.children, this@ChildrenFragment::updateChildItemsList)
    }

    private fun showAddChildDialog() {
        parentFragmentManager.let {
            bottomSheet = AddChildDialogFragment.newInstance(this).apply {
                show(it, tag)
            }
        }
    }

    private fun showRemoveChildDialog(child: Child) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.venue_children_remove_confirmation_title)
            .setMessage(R.string.venue_children_remove_confirmation_description)
            .setNegativeButton(R.string.action_no) { _, _ -> }
            .setPositiveButton(R.string.action_yes) { _, _ ->
                viewModel.removeChild(child)
                    .onErrorComplete()
                    .subscribeOn(Schedulers.io())
                    .subscribe()
            }.create()
            .show()
    }

    private fun updateChildItemsList(children: ChildListItemContainer) {
        with(binding) {
            actionBarTitleTextView.setText(R.string.add_children_title)
            childAddingDescriptionTextView.setText(if (children.isEmpty()) R.string.venue_children_empty_list_description else R.string.venue_children_list_description)
            childListView.isVisible = !children.isEmpty()
            emptyImageView.isVisible = children.isEmpty()
            primaryActionButton.isVisible = children.isEmpty()
        }
        childListAdapter.setChildItems(children)
    }

    companion object {
        const val CHECK_IN = "checkIn"
    }

}