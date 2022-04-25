package de.culture4life.luca.ui.myluca

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat.startActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import de.culture4life.luca.children.Child
import de.culture4life.luca.databinding.*
import de.culture4life.luca.idnow.IdNowManager
import de.culture4life.luca.registration.Person
import de.culture4life.luca.ui.idnow.IdNowEnrollFlowFragment
import de.culture4life.luca.ui.myluca.listitems.*
import de.culture4life.luca.ui.myluca.viewholders.*

class MyLucaListAdapter(private val clickListener: MyLucaListClickListener, private val fragment: Fragment) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items: MutableList<MyLucaListItemsWrapper> = mutableListOf()
    private val viewPagerPositionMap: MutableMap<Int, Int> = HashMap()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_EMPTY_STATE -> {
                val binding = ItemMyLucaEmptyBinding.inflate(layoutInflater, parent, false)
                EmptyStateViewHolder(binding)
            }
            TYPE_SECTION_HEADER -> {
                val binding = ItemMyLucaSectionHeaderBinding.inflate(layoutInflater, parent, false)
                SectionHeaderViewHolder(binding)
            }
            TYPE_DOCUMENT_SINGLE -> {
                val binding = ItemMyLucaDocumentBinding.inflate(layoutInflater, parent, false)
                SingleDocumentViewHolder(binding)
            }
            TYPE_DOCUMENT_MULTIPLE -> {
                val binding = ItemMyLucaViewpagerBinding.inflate(layoutInflater, parent, false)
                MultipleDocumentsViewHolder(binding)
            }
            TYPE_IDENTITY_EMPTY -> {
                val binding = ItemMyLucaIdentityEmptyBinding.inflate(layoutInflater, parent, false)
                IdentityEmptyViewHolder(binding)
            }
            TYPE_IDENTITY_QUEUED -> {
                val binding = ItemMyLucaIdentityQueuedBinding.inflate(layoutInflater, parent, false)
                IdentityQueuedViewHolder(binding)
            }
            TYPE_IDENTITY_REQUESTED -> {
                val binding = ItemMyLucaIdentityRequestedBinding.inflate(layoutInflater, parent, false)
                IdentityRequestedViewHolder(binding)
            }
            TYPE_IDENTITY_VERIFIED -> {
                val binding = ItemMyLucaIdentityVerifiedBinding.inflate(layoutInflater, parent, false)
                IdentityVerifiedViewHolder(binding)
            }
            else -> throw IllegalStateException(String.format("ViewType does not exist: ", viewType))
        }
    }

    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
        val itemsWrapper = getItem(position)
        val items = itemsWrapper.items
        when (viewHolder.itemViewType) {
            TYPE_EMPTY_STATE -> {
                // Nothing to do, all details are specified in the layout.
            }
            TYPE_SECTION_HEADER -> {
                val binding: ItemMyLucaSectionHeaderBinding = (viewHolder as SectionHeaderViewHolder).binding
                binding.personNameTextView.text = itemsWrapper.sectionHeader
                binding.personNameTextView.setCompoundDrawablesWithIntrinsicBounds(itemsWrapper.sectionDrawable(), 0, 0, 0)
            }
            TYPE_DOCUMENT_SINGLE -> {
                val holder = viewHolder as SingleDocumentViewHolder
                val item = items[0] as DocumentItem
                val expandClickListener = View.OnClickListener { clickListener.onExpandDocument(item, position) }
                val deleteClickListener = View.OnClickListener { clickListener.onDelete(item) }
                val iconClickListener = View.OnClickListener { clickListener.onIcon(item) }
                holder.show(item)
                holder.setListeners(expandClickListener, deleteClickListener, iconClickListener)
            }
            TYPE_DOCUMENT_MULTIPLE -> {
                val documentItems = itemsWrapper.documentItems()
                val hashCode = items.hashCode()
                val viewPagerAdapter = MyLucaDocumentItemViewPager(fragment, documentItems)
                val multipleHolder = viewHolder as MultipleDocumentsViewHolder
                multipleHolder.viewPager.adapter = viewPagerAdapter
                multipleHolder.pageIndicator.setViewPager2(multipleHolder.viewPager)
                val pageChangeCallback: OnPageChangeCallback = object : OnPageChangeCallback() {
                    override fun onPageSelected(positionInViewPager: Int) {
                        viewPagerPositionMap[hashCode] = positionInViewPager
                        super.onPageSelected(positionInViewPager)
                    }
                }
                multipleHolder.viewPager.unregisterOnPageChangeCallback(pageChangeCallback)
                multipleHolder.viewPager.registerOnPageChangeCallback(pageChangeCallback)
                var viewPagerStartPosition = items.size - 1 // show most recent item first
                if (viewPagerPositionMap.containsKey(hashCode)) {
                    viewPagerStartPosition = viewPagerPositionMap[hashCode]!!
                } else {
                    for (itemIndex in documentItems.indices.reversed()) {
                        val document = documentItems[itemIndex].document
                        if (document.isValidRecovery || document.isValidVaccination) {
                            viewPagerStartPosition = itemIndex // show last valid item first
                            break
                        }
                    }
                }
                multipleHolder.viewPager.setCurrentItem(viewPagerStartPosition, false)
            }
            TYPE_IDENTITY_EMPTY -> {
                val holder = viewHolder as IdentityEmptyViewHolder
                val addIdentityClickListener = View.OnClickListener {
                    IdNowEnrollFlowFragment.newInstance().show(fragment.parentFragmentManager, IdNowEnrollFlowFragment.TAG)
                }
                holder.setClickListener(addIdentityClickListener)
            }
            TYPE_IDENTITY_QUEUED -> {
                // Nothing to do, all details are specified in the layout.
            }
            TYPE_IDENTITY_REQUESTED -> {
                val holder = viewHolder as IdentityRequestedViewHolder
                val item = items[0] as IdentityRequestedItem
                val context = fragment.requireContext()

                holder.setClickListener { startActivity(context, IdNowManager.createIdNowIntent(context, item.token), null) }
                holder.setLongClickListener {
                    clickListener.onDelete(item)
                    true
                }
                holder.show(item)
            }
            TYPE_IDENTITY_VERIFIED -> {
                val holder = viewHolder as IdentityVerifiedViewHolder
                val item = items[0] as IdentityItem
                val expandClickListener = View.OnClickListener { clickListener.onExpandIdentity(item, position) }
                val deleteClickListener = View.OnClickListener { clickListener.onDelete(item) }
                val iconClickListener = View.OnClickListener { clickListener.onIcon(item) }
                holder.show(item)
                holder.setListeners(expandClickListener, deleteClickListener, iconClickListener)
            }
            else -> throw IllegalStateException("Missing case for view holder type: ${viewHolder.itemViewType}")
        }
    }

    private fun getItem(position: Int): MyLucaListItemsWrapper {
        return items[position]
    }

    override fun getItemCount(): Int {
        return items.size
    }

    // TODO Here we change the whole dataset, could be replaced by DiffUtil in the future
    @SuppressLint("NotifyDataSetChanged")
    fun setItems(items: List<MyLucaListItem>, persons: List<Person>): List<MyLucaListItemsWrapper> {
        val sortedList = sortAndPairItems(items, persons).toMutableList()
        this.items.clear()
        this.items.addAll(sortedList)
        notifyDataSetChanged()
        return sortedList
    }

    fun getWrapperWith(item: MyLucaListItem): MyLucaListItemsWrapper? {
        val parentItem = (item as? DocumentItem)?.document ?: return null
        items.forEach { wrapper ->
            wrapper.documentItems().forEach { wrapperItem ->
                if (wrapperItem.document.id == parentItem.id) {
                    return wrapper
                }
            }
        }
        return null
    }

    override fun getItemViewType(position: Int): Int {
        val wrapper = items[position]
        val item = wrapper.items.firstOrNull()
        return when {
            item is EmptyStateItem -> TYPE_EMPTY_STATE
            wrapper.isSectionHeader() -> TYPE_SECTION_HEADER
            wrapper.hasMultipleItems() -> TYPE_DOCUMENT_MULTIPLE
            item is DocumentItem -> TYPE_DOCUMENT_SINGLE
            item is IdentityEmptyItem -> TYPE_IDENTITY_EMPTY
            item is IdentityQueuedItem -> TYPE_IDENTITY_QUEUED
            item is IdentityRequestedItem -> TYPE_IDENTITY_REQUESTED
            item is IdentityItem -> TYPE_IDENTITY_VERIFIED
            else -> throw IllegalStateException("could not map item to list viewType")
        }
    }

    fun getPositionOfWrapper(myLucaListItemsWrapper: MyLucaListItemsWrapper): Int {
        return items.indexOf(myLucaListItemsWrapper)
    }

    interface MyLucaListClickListener {
        fun onDelete(myLucaListItem: MyLucaListItem)
        fun onIcon(myLucaListItem: MyLucaListItem)
        fun onExpandIdentity(identityItem: IdentityItem, position: Int)
        fun onExpandDocument(documentItem: DocumentItem, position: Int)
    }

    companion object {
        const val TYPE_EMPTY_STATE = 0
        const val TYPE_SECTION_HEADER = 1
        const val TYPE_DOCUMENT_SINGLE = 2
        const val TYPE_DOCUMENT_MULTIPLE = 3
        const val TYPE_IDENTITY_EMPTY = 4
        const val TYPE_IDENTITY_QUEUED = 5
        const val TYPE_IDENTITY_REQUESTED = 6
        const val TYPE_IDENTITY_VERIFIED = 7

        fun sortAndPairItems(list: List<MyLucaListItem>, persons: List<Person>): List<MyLucaListItemsWrapper> {
            val myLucaItems: MutableList<MyLucaListItemsWrapper> = ArrayList()
            if (shouldDisplayEmptyState(list)) {
                myLucaItems.add(MyLucaListItemsWrapper(EmptyStateItem()))
                if (list.isNotEmpty()) {
                    myLucaItems.add(MyLucaListItemsWrapper(list[0])) // include empty identity item
                }
            } else {
                for (person in persons) {
                    if (person is Child) {
                        myLucaItems.add(MyLucaListItemsWrapper(person.getFullName()))
                    }
                    val items = getSortedAndPairedItemsFor(list, person)
                    myLucaItems.addAll(items)
                }
            }
            return myLucaItems
        }

        private fun getSortedAndPairedItemsFor(list: List<MyLucaListItem>, person: Person): List<MyLucaListItemsWrapper> {
            val vaccinationItems: MutableList<DocumentItem> = ArrayList()
            val sortedList: MutableList<MyLucaListItemsWrapper> = ArrayList()
            for (listItem in list) {
                // Skip if document is owned by a different person.
                //  But don't skip if it's an identity card in combination with an adult person. The identity item doesn't contain information to
                //  which person it belongs. This does work because the app is restricted to one adult only yet, otherwise every adult would get the
                //  identity card added.
                if (!isFrom(listItem, person) && !(isIdentityItem(listItem) && person !is Child)) {
                    continue
                }
                if (listItem is VaccinationItem) {
                    vaccinationItems.add(listItem)
                } else {
                    sortedList.add(MyLucaListItemsWrapper(listItem, person is Child))
                }
            }
            if (vaccinationItems.isNotEmpty()) {
                sortedList.add(MyLucaListItemsWrapper(vaccinationItems, person is Child))
            }
            sortedList.sortWith { (items1, timestamp1), (items2, timestamp2) ->
                if (isIdentityItem(items1[0])) return@sortWith 1
                if (isIdentityItem(items2[0])) return@sortWith -1
                timestamp2.compareTo(timestamp1)
            }
            return sortedList
        }

        private fun shouldDisplayEmptyState(items: List<MyLucaListItem>): Boolean {
            val hasOnlyEmptyIdentityItem = items.size == 1 && items[0] is IdentityEmptyItem
            return items.isEmpty() || hasOnlyEmptyIdentityItem
        }

        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        fun isFrom(item: MyLucaListItem, person: Person): Boolean {
            return when {
                item !is DocumentItem -> false
                item.document.firstName == null -> person !is Child
                else -> item.document.owner.equalsSimplified(person)
            }
        }

        private fun isIdentityItem(item: MyLucaListItem): Boolean {
            return item is IdentityItem || item is IdentityEmptyItem || item is IdentityQueuedItem || item is IdentityRequestedItem
        }
    }
}
