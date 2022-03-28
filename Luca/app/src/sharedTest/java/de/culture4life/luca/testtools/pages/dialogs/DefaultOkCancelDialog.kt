package de.culture4life.luca.testtools.pages.dialogs

abstract class DefaultOkCancelDialog : DefaultOkDialog() {

    val cancelButton = baseDialog.negativeButton
        .also(::interceptButtonOnPerform)
}
