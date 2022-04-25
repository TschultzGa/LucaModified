package de.culture4life.luca.ui.myluca.viewholders

import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.tbuonomo.viewpagerdotsindicator.SpringDotsIndicator
import de.culture4life.luca.R
import de.culture4life.luca.databinding.ItemMyLucaViewpagerBinding

class MultipleDocumentsViewHolder(itemView: ItemMyLucaViewpagerBinding) : RecyclerView.ViewHolder(itemView.root) {

    val viewPager: ViewPager2 = itemView.myLucaItemsViewPager
        .apply { addItemDecoration(MarginItemDecoration(context, R.dimen.spacing_default)) }

    val pageIndicator: SpringDotsIndicator = itemView.myLucaItemsViewPagerIndicator
}
