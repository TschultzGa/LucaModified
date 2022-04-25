package de.culture4life.luca.ui.myluca

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import de.culture4life.luca.ui.myluca.listitems.DocumentItem

class MyLucaDocumentItemViewPager(
    fragment: Fragment,
    private val items: List<DocumentItem>
) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = items.size

    override fun createFragment(position: Int): Fragment {
        return MyLucaDocumentItemForViewPagerFragment.newInstance(items[position].document.id)
    }
}
