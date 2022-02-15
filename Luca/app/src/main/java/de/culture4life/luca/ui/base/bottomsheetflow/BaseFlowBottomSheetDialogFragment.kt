package de.culture4life.luca.ui.base.bottomsheetflow

import android.content.DialogInterface
import android.view.View
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import de.culture4life.luca.LucaApplication
import de.culture4life.luca.databinding.BottomSheetFlowBinding
import de.culture4life.luca.ui.base.BaseBottomSheetDialogFragment

abstract class BaseFlowBottomSheetDialogFragment<ViewModelType : BaseFlowViewModel> : BaseBottomSheetDialogFragment<ViewModelType>() {

    lateinit var binding: BottomSheetFlowBinding
    lateinit var pagerAdapter: FlowPageAdapter
    override var fixedHeight = true

    // Disable animations for automatic tests to avoid flakiness. Espresso does not always wait until all animations are done.
    private val useSmoothScrollAnimation = !LucaApplication.isRunningInstrumentationTests()

    abstract fun lastPageHasBackButton(): Boolean

    override fun getViewBinding(): ViewBinding {
        binding = BottomSheetFlowBinding.inflate(layoutInflater)
        return binding
    }

    override fun initializeViews() {
        super.initializeViews()
        initializeViewPager()
        initializeObservers()
    }

    private fun initializeViewPager() {
        pagerAdapter = FlowPageAdapter(this)

        binding.confirmationStepViewPager.apply {
            adapter = pagerAdapter
            isUserInputEnabled = false
            offscreenPageLimit = 2

            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    val isFirstPage = position == 0
                    val isLastPage = pagerAdapter.itemCount - 1 == position
                    val isHidden = isFirstPage || (isLastPage && !lastPageHasBackButton())
                    binding.backButton.visibility = if (isHidden) View.INVISIBLE else View.VISIBLE
                }
            })
        }
    }

    private fun initializeObservers() {
        binding.backButton.setOnClickListener { navigateToPrevious() }
        binding.cancelButton.setOnClickListener { dismiss() }

        viewModel.onPagesUpdated.observe(viewLifecycleOwner, {
            if (!it.hasBeenHandled()) {
                pagerAdapter.setPages(it.valueAndMarkAsHandled)
            }
        })

        viewModel.pagerNavigation.observe(viewLifecycleOwner, {
            if (!it.hasBeenHandled()) {
                when (it.valueAndMarkAsHandled) {
                    BaseFlowViewModel.PagerNavigate.NEXT -> navigateToNext()
                    BaseFlowViewModel.PagerNavigate.PREVIOUS -> navigateToPrevious()
                }
            }
        })
    }

    private fun navigateToPrevious() {
        val current = binding.confirmationStepViewPager.currentItem

        if (current > 0) {
            binding.confirmationStepViewPager.setCurrentItem(current - 1, useSmoothScrollAnimation)
        }
    }

    protected fun navigateToNext() {
        val current = binding.confirmationStepViewPager.currentItem

        when {
            pagerAdapter.hasItemAt(current + 1) ->
                binding.confirmationStepViewPager.setCurrentItem(current + 1, useSmoothScrollAnimation)

            current == (pagerAdapter.itemCount - 1) -> viewModel.onFinishFlow()
        }
    }

    fun disableVerticalViewPagerScrolling() {
        binding.confirmationStepViewPager.apply {
            // access underlying RecyclerView and disable scrolling so BottomSheet can scroll vertically
            getChildAt(0).apply {
                isNestedScrollingEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
            }
        }
    }

    fun enableVerticalViewPagerScrolling() {
        binding.confirmationStepViewPager.apply {
            // access underlying RecyclerView and disable scrolling so BottomSheet can scroll vertically
            getChildAt(0).apply {
                isNestedScrollingEnabled = true
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            }
        }
    }

    class FlowPageAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        private val pages: MutableList<BaseFlowChildFragment<*, *>> = mutableListOf()

        fun addPage(fragment: BaseFlowChildFragment<*, *>) {
            pages.add(fragment)
            notifyItemInserted(pages.indexOf(fragment))
        }

        fun setPages(pages: List<BaseFlowChildFragment<*, *>>) {
            this.pages.apply {
                clear()
                addAll(pages)
            }
            notifyDataSetChanged()
        }

        fun removePageAtIndex(index: Int) {
            pages.removeAt(index)
            notifyItemRemoved(index)
        }

        fun addPageAtIndex(index: Int, page: BaseFlowChildFragment<*, *>) {
            pages.add(index, page)
            notifyItemInserted(index)
        }

        fun clearPages() {
            pages.clear()
            notifyDataSetChanged()
        }

        fun hasItemAt(position: Int) = position >= 0 && position <= pages.lastIndex

        override fun getItemCount(): Int = pages.size
        override fun createFragment(position: Int): Fragment = pages[position]

        override fun getItemId(position: Int): Long {
            return pages[position].hashCode().toLong()
        }

        override fun containsItem(itemId: Long): Boolean {
            return pages.any { it.hashCode().toLong() == itemId }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        binding.confirmationStepViewPager.adapter = null
        super.onDismiss(dialog)
    }

}