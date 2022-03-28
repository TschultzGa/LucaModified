package de.culture4life.luca.ui.children

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import de.culture4life.luca.R
import de.culture4life.luca.children.Child
import de.culture4life.luca.databinding.ItemChildBinding
import io.reactivex.rxjava3.schedulers.Schedulers

const val CHILD_NAME_VIEW_TYPE = 0
const val ADD_CHILD_BUTTON_VIEW_TYPE = 1

class ChildListAdapter(
    context: Context,
    resource: Int,
    private val viewModel: ChildrenViewModel,
    private val isCheckedIn: Boolean,
    private val removeChildListener: (Child) -> Unit,
    private val addChildListener: () -> Unit
) : KeyboardInputTrackingAdapter<ChildListItem>(context, resource) {

    enum class KeyboardSelectionField {
        CHECKBOX,
        DELETE,
        ADD_CHILD_BUTTON
    }

    private data class KeyboardFocus(val position: Int, val field: KeyboardSelectionField?)

    private var currentKeyboardFocus = KeyboardFocus(-1, null)
    val layoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    override fun getCount(): Int {
        return super.getCount() + 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == count - 1) ADD_CHILD_BUTTON_VIEW_TYPE else CHILD_NAME_VIEW_TYPE
    }

    override fun getViewTypeCount(): Int {
        return 2
    }

    fun setChildItems(items: List<ChildListItem>) {
        clear()
        addAll(items)
        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, container: ViewGroup): View {
        return if (getItemViewType(position) == ADD_CHILD_BUTTON_VIEW_TYPE) getAddChildButtonView(
            convertView,
            container,
            position
        ) else getChildView(position, convertView)
    }

    private fun getChildView(position: Int, convertView: View?): View {
        // for smooth scrolling
        val binding = if (convertView == null) {
            ItemChildBinding.inflate(layoutInflater)
        } else {
            ItemChildBinding.bind(convertView)
        }

        getItem(position)?.let { childItem ->
            binding.includeChildCheckBox.apply {
                isChecked = childItem.isCheckedIn
                isVisible = isCheckedIn
                val isCheckboxFocused = currentKeyboardFocus.position == position && currentKeyboardFocus.field == KeyboardSelectionField.CHECKBOX
                background =
                    if (isCheckboxFocused) ColorDrawable(
                        ContextCompat.getColor(context, R.color.primaryColor)
                    ) else ColorDrawable(Color.TRANSPARENT)
                setOnClickListener { toggleCheckIn(childItem) }
            }

            binding.childNameTextView.apply {
                text = childItem.child.getFullName()
                if (isCheckedIn) {
                    setOnClickListener {
                        toggleCheckIn(childItem)
                        binding.includeChildCheckBox.toggle()
                    }
                }
            }

            binding.removeChildImageView.apply {
                val isRemoveChildButtonFocused =
                    currentKeyboardFocus.position == position && currentKeyboardFocus.field == KeyboardSelectionField.DELETE
                background =
                    if (isRemoveChildButtonFocused) ColorDrawable(
                        ContextCompat.getColor(context, R.color.primaryColor)
                    ) else ColorDrawable(
                        Color.TRANSPARENT
                    )
                setOnClickListener { removeChildListener(childItem.child) }
            }
        }
        return binding.root
    }

    private fun getAddChildButtonView(
        convertView: View?,
        container: ViewGroup,
        position: Int
    ): View {
        val view = convertView ?: layoutInflater.inflate(R.layout.layout_add_child_button, container, false)
        val addChildLinearLayout = view.findViewById<LinearLayout>(R.id.addChildLayout)
        val isAddChildButtonFocused =
            currentKeyboardFocus.position == position && currentKeyboardFocus.field == KeyboardSelectionField.ADD_CHILD_BUTTON
        addChildLinearLayout.background =
            if (isAddChildButtonFocused)
                ColorDrawable(
                    ContextCompat.getColor(context, R.color.primaryColor)
                ) else ColorDrawable(Color.TRANSPARENT)
        addChildLinearLayout.setOnClickListener { addChildListener() }
        return view
    }

    private fun toggleCheckIn(childItem: ChildListItem) {
        viewModel.toggleCheckIn(childItem)
            .onErrorComplete()
            .subscribeOn(Schedulers.io())
            .subscribe()
    }

    fun handleKeyboardInput(keyboardInputEvent: KeyboardInputEvents, position: Int) {
        when {
            keyboardInputEvent == KeyboardInputEvents.ENTER_PRESSED -> handleEnterPressed()
            keyboardInputEvent == KeyboardInputEvents.LEFT_PRESSED && getItemViewType(position) == CHILD_NAME_VIEW_TYPE && isCheckedIn -> setKeyboardSelection(
                position,
                KeyboardSelectionField.CHECKBOX
            )
            keyboardInputEvent == KeyboardInputEvents.RIGHT_PRESSED && getItemViewType(position) == CHILD_NAME_VIEW_TYPE && isCheckedIn -> setKeyboardSelection(
                position,
                KeyboardSelectionField.DELETE
            )
            currentKeyboardFocus.position != position && keyboardInputEvent == KeyboardInputEvents.UPDATE_SELECTED_ITEM_POSITION -> {
                val selectedField = when {
                    (getItemViewType(position) == ADD_CHILD_BUTTON_VIEW_TYPE) -> KeyboardSelectionField.ADD_CHILD_BUTTON
                    (isCheckedIn) -> KeyboardSelectionField.CHECKBOX
                    else -> KeyboardSelectionField.DELETE
                }
                setKeyboardSelection(position, selectedField)
            }
        }
    }

    private fun handleEnterPressed() {
        when (currentKeyboardFocus.field) {
            KeyboardSelectionField.ADD_CHILD_BUTTON -> addChildListener()
            KeyboardSelectionField.DELETE -> getItem(currentKeyboardFocus.position)?.child?.let {
                removeChildListener(it)
            }
            KeyboardSelectionField.CHECKBOX -> getItem(currentKeyboardFocus.position)?.let {
                it.toggleIsChecked()
                notifyDataSetChanged()
            }
        }
    }

    fun setKeyboardSelection(position: Int, field: KeyboardSelectionField?) {
        currentKeyboardFocus = KeyboardFocus(position, field)
        notifyDataSetChanged()
    }
}
