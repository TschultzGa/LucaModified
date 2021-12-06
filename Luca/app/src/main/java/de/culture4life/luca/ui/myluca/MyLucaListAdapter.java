package de.culture4life.luca.ui.myluca;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.culture4life.luca.R;
import de.culture4life.luca.children.Child;
import de.culture4life.luca.databinding.ItemMyLucaSectionHeaderBinding;
import de.culture4life.luca.document.Document;
import de.culture4life.luca.registration.Person;
import de.culture4life.luca.ui.myluca.viewholders.MultipleMyLucaItemViewHolder;
import de.culture4life.luca.ui.myluca.viewholders.SectionHeaderViewHolder;
import de.culture4life.luca.ui.myluca.viewholders.SingleMyLucaItemViewHolder;

public class MyLucaListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface MyLucaListClickListener {
        void onDelete(@NonNull MyLucaListItem myLucaListItem);
    }

    public static final int SINGLE_ITEM_VIEW_HOLDER = 0;
    public static final int MULTIPLE_ITEM_VIEW_HOLDER = 1;
    public static final int SECTION_HEADER_ITEM_VIEW_HOLDER = 2;

    private final MyLucaListClickListener clickListener;
    private final Fragment fragment;

    private final List<MyLucaListItemsWrapper> items = new ArrayList<>();
    private final Map<Integer, Integer> viewPagerPositionMap = new HashMap<>();

    public MyLucaListAdapter(MyLucaListClickListener listener, Fragment fragment) {
        this.clickListener = listener;
        this.fragment = fragment;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == SINGLE_ITEM_VIEW_HOLDER) {
            SingleLucaItemView view = new SingleLucaItemView(parent.getContext());
            return new SingleMyLucaItemViewHolder(view.getBinding());
        } else if (viewType == MULTIPLE_ITEM_VIEW_HOLDER) {
            ViewGroup view = (ViewGroup) LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_my_luca_viewpager, parent, false);
            return new MultipleMyLucaItemViewHolder(view);
        } else if (viewType == SECTION_HEADER_ITEM_VIEW_HOLDER) {
            ItemMyLucaSectionHeaderBinding binding = ItemMyLucaSectionHeaderBinding.inflate(LayoutInflater.from(parent.getContext()));
            return new SectionHeaderViewHolder(binding);
        } else {
            throw new IllegalStateException(String.format("ViewType does not exist: ", viewType));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
        MyLucaListItemsWrapper itemsWrapper = getItem(position);
        List<MyLucaListItem> items = itemsWrapper.getItems();

        if (viewHolder.getItemViewType() == SINGLE_ITEM_VIEW_HOLDER) {
            SingleMyLucaItemViewHolder holder = (SingleMyLucaItemViewHolder) viewHolder;
            MyLucaListItem item = items.get(0);

            View.OnClickListener expandClickListener = (v -> {
                item.toggleExpanded();
                notifyItemChanged(position);
            });
            View.OnClickListener deleteClickListener = (v -> clickListener.onDelete(item));

            holder.show(item);
            holder.setListeners(expandClickListener, deleteClickListener);
            setLeftPaddingForChild(holder.getBinding().getRoot(), itemsWrapper.isChildSection());
        } else if (viewHolder.getItemViewType() == MULTIPLE_ITEM_VIEW_HOLDER) {
            Integer hashCode = items.hashCode();
            MyLucaItemViewPager viewPagerAdapter = new MyLucaItemViewPager(this.fragment, items);
            MultipleMyLucaItemViewHolder multipleHolder = (MultipleMyLucaItemViewHolder) viewHolder;
            multipleHolder.getViewPager().setAdapter(viewPagerAdapter);
            multipleHolder.getPageIndicator().setViewPager2(multipleHolder.getViewPager());
            ViewPager2.OnPageChangeCallback pageChangeCallback = new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int positionInViewPager) {
                    viewPagerPositionMap.put(hashCode, positionInViewPager);
                    super.onPageSelected(positionInViewPager);
                }
            };
            multipleHolder.getViewPager().unregisterOnPageChangeCallback(pageChangeCallback);
            multipleHolder.getViewPager().registerOnPageChangeCallback(pageChangeCallback);

            int viewPagerStartPosition = items.size() - 1; // show most recent item first
            if (viewPagerPositionMap.containsKey(hashCode)) {
                viewPagerStartPosition = viewPagerPositionMap.get(hashCode);
            } else {
                for (int itemIndex = items.size() - 1; itemIndex >= 0; itemIndex--) {
                    Document document = items.get(itemIndex).document;
                    if (document.isValidRecovery() || document.isValidVaccination()) {
                        viewPagerStartPosition = itemIndex; // show last valid item first
                        break;
                    }
                }
            }
            multipleHolder.getViewPager().setCurrentItem(viewPagerStartPosition, false);

            setLeftPaddingForChild(multipleHolder.getViewPager(), itemsWrapper.isChildSection());
        } else if (viewHolder.getItemViewType() == SECTION_HEADER_ITEM_VIEW_HOLDER) {
            ItemMyLucaSectionHeaderBinding binding = ((SectionHeaderViewHolder) viewHolder).getBinding();
            binding.personNameTextView.setText(itemsWrapper.getSectionHeader());
            binding.personNameTextView.setCompoundDrawablesWithIntrinsicBounds(itemsWrapper.sectionDrawable(), 0, 0, 0);
        }
    }

    private void setLeftPaddingForChild(View view, boolean isChild) {
        int margin = (int) view.getContext().getResources().getDimension(R.dimen.spacing_default);
        view.setPadding(isChild ? margin : 0, 0, 0, 0);
    }

    private MyLucaListItemsWrapper getItem(int position) {
        return items.get(position);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public List<MyLucaListItemsWrapper> setItems(@NonNull List<MyLucaListItem> items, List<Person> persons) {
        List<MyLucaListItemsWrapper> sortedList = sortAndPairItems(items, persons);
        this.items.clear();
        this.items.addAll(sortedList);
        notifyDataSetChanged();
        return sortedList;
    }

    @Nullable
    public MyLucaListItemsWrapper getWrapperWith(@NonNull MyLucaListItem item) {
        for (MyLucaListItemsWrapper wrapper : items) {
            if (!wrapper.getItems().isEmpty()) {
                if (wrapper.hasMultipleItems()) {
                    for (MyLucaListItem wrapperItem : wrapper.getItems()) {
                        if (wrapperItem.document.getId().equals(item.document.getId())) {
                            return wrapper;
                        }
                    }
                } else if (wrapper.getItems().get(0).document.getId().equals(item.document.getId())) {
                    return wrapper;
                }
            }
        }
        return null;
    }

    @Override
    public int getItemViewType(int position) {
        MyLucaListItemsWrapper item = this.items.get(position);
        if (item.isSectionHeader()) {
            return SECTION_HEADER_ITEM_VIEW_HOLDER;
        } else if (item.hasMultipleItems()) {
            return MULTIPLE_ITEM_VIEW_HOLDER;
        } else {
            return SINGLE_ITEM_VIEW_HOLDER;
        }
    }

    protected static List<MyLucaListItemsWrapper> sortAndPairItems(@NonNull List<MyLucaListItem> list, @NonNull List<Person> persons) {
        List<MyLucaListItemsWrapper> myLucaItems = new ArrayList<>();
        int visibleDocuments = 0;
        for (Person person : persons) {
            myLucaItems.add(new MyLucaListItemsWrapper(person.getFullName(), person instanceof Child));
            List<MyLucaListItemsWrapper> items = sortedAndPairedItemsFor(list, person);
            myLucaItems.addAll(items);
            visibleDocuments += items.size();
        }
        if (visibleDocuments == 0) myLucaItems.clear();
        return myLucaItems;
    }

    protected static List<MyLucaListItemsWrapper> sortedAndPairedItemsFor(@NonNull List<MyLucaListItem> list, @NonNull Person person) {
        List<MyLucaListItem> vaccinationItems = new ArrayList<>();
        List<MyLucaListItemsWrapper> sortedList = new ArrayList<>();
        for (MyLucaListItem listItem : list) {
            if (isFrom(listItem, person)) {
                if (listItem.getClass().equals(VaccinationItem.class)) {
                    vaccinationItems.add(listItem);
                } else {
                    sortedList.add(new MyLucaListItemsWrapper(listItem, person instanceof Child));
                }
            }
        }
        if (!vaccinationItems.isEmpty()) {
            sortedList.add(new MyLucaListItemsWrapper(vaccinationItems, person instanceof Child));
        }
        Collections.sort(sortedList, (first, second) -> Long.compare(second.getTimeStamp(), first.getTimeStamp()));
        return sortedList;
    }

    protected static boolean isFrom(@NonNull MyLucaListItem item, @NonNull Person person) {
        if (item.getDocument().getFirstName() == null) {
            return !(person instanceof Child);
        }
        return item.getDocument().getOwner().equalsSimplified(person);
    }

}