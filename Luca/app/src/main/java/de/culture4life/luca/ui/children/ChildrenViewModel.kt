package de.culture4life.luca.ui.children

import android.app.Application
import androidx.lifecycle.MutableLiveData
import de.culture4life.luca.R
import de.culture4life.luca.children.Child
import de.culture4life.luca.ui.BaseViewModel
import io.reactivex.rxjava3.core.Completable

class ChildrenViewModel(application: Application) : BaseViewModel(application) {

    val children: MutableLiveData<ChildListItemContainer> = MutableLiveData()
    private val childrenManager = this.application.childrenManager
    private val registrationManager = this.application.registrationManager
    private val documentManager = this.application.documentManager

    override fun initialize(): Completable {
        return super.initialize()
            .andThen(childrenManager.initialize(application))
            .andThen(registrationManager.initialize(application))
    }

    fun navigateBack() {
        navigationController?.popBackStack()
    }

    @JvmOverloads
    fun addChild(child: Child, checkIn: Boolean = false): Completable {
        return childrenManager.containsChild(child)
            .flatMapCompletable { childExists ->
                if (childExists) {
                    Completable.complete()
                } else {
                    childrenManager.addChild(child, checkIn)
                        .andThen(updateList())
                }
            }
            .doOnError { throwable ->
                val viewError = createErrorBuilder(throwable)
                    .withTitle(R.string.venue_children_add_error_title)
                    .removeWhenShown()
                    .build()
                addError(viewError)
            }
    }

    fun removeChild(child: Child): Completable {
        return childrenManager.removeChild(child)
            .andThen(documentManager.reImportDocuments())
            .andThen(updateList())
            .doOnError { throwable ->
                addChild(child)
                    .onErrorComplete()
                    .subscribe()
                val viewError = createErrorBuilder(throwable)
                    .withTitle(R.string.venue_children_remove_error_title)
                    .removeWhenShown()
                    .build()
                addError(viewError)
            }
    }

    fun updateList(): Completable {
        return childrenManager.getChildren()
            .map { children ->
                ChildListItemContainer().apply {
                    for (child in children) {
                        val isCheckedIn = childrenManager.isCheckedIn(child).blockingGet()
                        add(ChildListItem(child, isCheckedIn))
                    }
                }
            }
            .flatMapCompletable { update(children, it) }
    }

    fun restoreChildren(): Completable {
        return childrenManager.getChildren()
            .map { children ->
                ChildListItemContainer(
                    children.map { child ->
                        ChildListItem(
                            child,
                            childrenManager.isCheckedIn(child).blockingGet()
                        )
                    }
                )
            }
            .flatMapCompletable { update(children, it) }
    }

    fun toggleCheckIn(childItem: ChildListItem): Completable {
        return Completable.defer {
            childItem.toggleIsChecked()
            if (childItem.isCheckedIn) {
                childrenManager.checkIn(childItem.child)
            } else {
                childrenManager.checkOut(childItem.child)
            }
        }
    }
}
