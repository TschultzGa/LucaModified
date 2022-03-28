package de.culture4life.luca.ui.whatisnew

import android.content.Intent
import android.os.Bundle
import androidx.viewpager2.widget.ViewPager2
import de.culture4life.luca.R
import de.culture4life.luca.databinding.ActivityWhatIsNewBinding
import de.culture4life.luca.ui.BaseActivity
import de.culture4life.luca.ui.MainActivity
import de.culture4life.luca.whatisnew.WhatIsNewManager
import de.culture4life.luca.whatisnew.WhatIsNewPage
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.functions.Predicate
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber

class WhatIsNewActivity : BaseActivity() {

    private lateinit var binding: ActivityWhatIsNewBinding
    private var adapter: WhatIsNewViewPagerAdapter? = null
    private var pages: List<WhatIsNewPage>? = null
    private var currentViewPagerPosition = 0
    private val hasPreviousPage: Boolean
        get() = currentViewPagerPosition > 0
    private val hasNextPage: Boolean
        get() = currentViewPagerPosition < (pages?.size ?: 0) - 1

    private var finishOnExit = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWhatIsNewBinding.inflate(layoutInflater)
        setupViews()

        val onlyUnseenPages = intent?.extras?.getBoolean(SHOW_ONLY_UNSEEN_PAGES, true) ?: true
        val pageGroup: WhatIsNewManager.PageGroup? = intent?.extras?.getSerializable(SHOW_PAGE_GROUP) as WhatIsNewManager.PageGroup?
        finishOnExit = intent?.extras?.getBoolean(FINISH_ON_EXIT, false) ?: false

        initializePages(onlyUnseenPages, pageGroup)
        hideActionBar()
    }

    private fun setupViews() {
        setContentView(binding.root)
        setClickListeners()
    }

    private fun initializePages(onlyUnseenPages: Boolean = true, pageGroup: WhatIsNewManager.PageGroup? = null) {
        val pagesToDisplay = if (onlyUnseenPages) {
            application.whatIsNewManager.getUnseenPages()
        } else {
            application.whatIsNewManager.getAllPages()
        }

        val filterByPageGroup = if (pageGroup != null) {
            Predicate<WhatIsNewPage> {
                it.index >= pageGroup.value.startIndex && it.index < (pageGroup.value.startIndex + pageGroup.value.size)
            }
        } else {
            Predicate<WhatIsNewPage> { true }
        }

        pagesToDisplay
            .filter(filterByPageGroup)
            .toList()
            .doOnSuccess { this.pages = it }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { initializeViewPager() },
                { Timber.e("Unable to load unseen pages: %s", it.toString()) }
            )
    }

    private fun initializeViewPager() {
        pages?.let {
            adapter = WhatIsNewViewPagerAdapter(supportFragmentManager, this.lifecycle, it)
            binding.whatIsNewViewPager.adapter = adapter
        }

        binding.whatIsNewPagesIndicator.setViewPager2(binding.whatIsNewViewPager)
        val onPageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateBottomButtonsLabels(position)
                pages?.get(position)?.index?.let { index ->
                    application.whatIsNewManager.markPageAsSeen(index)
                        .onErrorComplete()
                        .subscribeOn(Schedulers.io())
                        .subscribe()
                }
            }
        }
        binding.whatIsNewViewPager.unregisterOnPageChangeCallback(onPageChangeCallback)
        binding.whatIsNewViewPager.registerOnPageChangeCallback(onPageChangeCallback)
    }

    private fun updateBottomButtonsLabels(position: Int) {
        currentViewPagerPosition = position
        if (pages == null) return
        with(binding) {
            skipOrGoBackButton.text = if (hasPreviousPage) {
                getString(R.string.what_is_new_back)
            } else {
                getString(R.string.what_is_new_skip)
            }
            nextButton.text = if (hasNextPage) {
                getString(R.string.what_is_new_next)
            } else {
                getString(R.string.what_is_new_close)
            }
        }
    }

    private fun setClickListeners() {
        binding.skipOrGoBackButton.setOnClickListener {
            if (hasPreviousPage) {
                binding.whatIsNewViewPager.currentItem = currentViewPagerPosition - 1
            } else {
                showMainApp()
            }
        }

        binding.nextButton.setOnClickListener {
            if (hasNextPage) {
                binding.whatIsNewViewPager.currentItem = currentViewPagerPosition + 1
            } else {
                showMainApp()
            }
        }
    }

    private fun showMainApp() {
        if (!finishOnExit) {
            application.whatIsNewManager.disableWhatIsNewScreenForCurrentVersion()
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
        }
        finish()
    }

    companion object {

        const val SHOW_ONLY_UNSEEN_PAGES = "show_only_unseen_pages"
        const val SHOW_PAGE_GROUP = "show_page_group"
        const val FINISH_ON_EXIT = "finish_on_exit"
    }
}
