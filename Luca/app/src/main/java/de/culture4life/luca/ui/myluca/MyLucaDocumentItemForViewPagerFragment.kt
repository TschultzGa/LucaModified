package de.culture4life.luca.ui.myluca

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import de.culture4life.luca.databinding.ItemMyLucaDocumentBinding
import de.culture4life.luca.ui.myluca.listitems.DocumentItem
import de.culture4life.luca.ui.myluca.viewholders.SingleDocumentViewHolder

class MyLucaDocumentItemForViewPagerFragment : Fragment() {

    private val viewModel by activityViewModels<MyLucaViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val layoutInflater = LayoutInflater.from(context)
        val binding = ItemMyLucaDocumentBinding.inflate(layoutInflater, container, false)
        val itemViewHolder = SingleDocumentViewHolder(binding)
        viewModel.myLucaItems.value?.let { myLucaItems ->
            arguments?.getString(KEY_DOCUMENT_ID)?.let { itemId ->
                myLucaItems.filterIsInstance<DocumentItem>().firstOrNull { it.document.id == itemId }?.let { item ->
                    itemViewHolder.show(item)
                    itemViewHolder.setListeners(
                        { viewModel.onItemExpandToggleRequested(item) },
                        { viewModel.onItemDeletionRequested(item) }
                    )
                }
            }
        }
        return itemViewHolder.binding.root
    }

    companion object {
        const val KEY_DOCUMENT_ID = "DocumentId"

        fun newInstance(itemDocumentId: String): MyLucaDocumentItemForViewPagerFragment {
            return MyLucaDocumentItemForViewPagerFragment().apply {
                arguments = bundleOf(Pair(KEY_DOCUMENT_ID, itemDocumentId))
            }
        }
    }
}
