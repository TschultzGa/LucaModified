package de.culture4life.luca.ui.whatisnew

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import de.culture4life.luca.ui.whatisnew.WhatIsNewPageFragment.Companion.PAGE_DESCRIPTION_KEY
import de.culture4life.luca.ui.whatisnew.WhatIsNewPageFragment.Companion.PAGE_HEADING_KEY
import de.culture4life.luca.ui.whatisnew.WhatIsNewPageFragment.Companion.PAGE_IMAGE_RES_KEY
import de.culture4life.luca.whatisnew.WhatIsNewPage

class WhatIsNewViewPagerAdapter(
    fragmentManager: FragmentManager,
    lifecycle: Lifecycle,
    private val items: List<WhatIsNewPage>
) : FragmentStateAdapter(fragmentManager, lifecycle) {

    override fun getItemCount(): Int = items.size

    override fun createFragment(position: Int): Fragment {
        val pageItem = items[position]
        val pageFragment = WhatIsNewPageFragment()
        val bundle = Bundle()
        pageItem.image?.let { bundle.putInt(PAGE_IMAGE_RES_KEY, it) }
        bundle.putString(PAGE_HEADING_KEY, pageItem.heading)
        bundle.putString(PAGE_DESCRIPTION_KEY, pageItem.description)
        pageFragment.arguments = bundle
        return pageFragment
    }
}
