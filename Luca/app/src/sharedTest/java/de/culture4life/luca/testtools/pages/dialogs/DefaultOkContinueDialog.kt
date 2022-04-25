package de.culture4life.luca.testtools.pages.dialogs

abstract class DefaultOkContinueDialog : DefaultOkDialog() {

    val continueButton = baseDialog.neutralButton
        .also(::interceptButtonOnPerform)
}
