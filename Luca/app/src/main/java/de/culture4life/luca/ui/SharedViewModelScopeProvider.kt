package de.culture4life.luca.ui

import androidx.lifecycle.ViewModelStoreOwner

/**
 * Provides the [ViewModelStoreOwner] to use for [androidx.lifecycle.ViewModel]s that are shared with own child fragments.
 */
interface SharedViewModelScopeProvider {

    val sharedViewModelStoreOwner: ViewModelStoreOwner
}
