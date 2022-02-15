package de.culture4life.luca.ui.account.news

import android.content.Intent
import androidx.core.os.bundleOf
import androidx.viewbinding.ViewBinding
import de.culture4life.luca.databinding.FragmentNewsBinding
import de.culture4life.luca.ui.BaseFragment
import de.culture4life.luca.ui.whatisnew.WhatIsNewActivity
import de.culture4life.luca.whatisnew.WhatIsNewManager

class NewsFragment : BaseFragment<NewsViewModel>() {

    private lateinit var binding: FragmentNewsBinding

    override fun getViewBinding(): ViewBinding {
        binding = FragmentNewsBinding.inflate(layoutInflater)
        return binding
    }

    override fun getViewModelClass(): Class<NewsViewModel> =
        NewsViewModel::class.java

    override fun initializeViews() {
        super.initializeViews()
        initializeObservers()
    }

    private fun initializeObservers() {
        binding.pageGroup1.setOnClickListener { showPageGroup(WhatIsNewManager.PageGroup.LUCA_2_0) }
        binding.pageGroup2.setOnClickListener { showPageGroup(WhatIsNewManager.PageGroup.LUCA_2_2) }
        binding.pageGroup3.setOnClickListener { showPageGroup(WhatIsNewManager.PageGroup.LUCA_2_4) }
    }

    private fun showPageGroup(pageGroup: WhatIsNewManager.PageGroup) {
        val intent = Intent(context, WhatIsNewActivity::class.java)
        intent.apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            val bundle = bundleOf(
                Pair(WhatIsNewActivity.SHOW_ONLY_UNSEEN_PAGES, false),
                Pair(WhatIsNewActivity.FINISH_ON_EXIT, true),
                Pair(WhatIsNewActivity.SHOW_PAGE_GROUP, pageGroup)
            )
            putExtras(bundle)
        }

        startActivity(intent)
    }
}