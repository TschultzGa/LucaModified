package de.culture4life.luca.testtools.preconditions

import androidx.test.core.app.ApplicationProvider
import de.culture4life.luca.LucaApplication

class WhatIsNewPreconditions {

    private val application = ApplicationProvider.getApplicationContext<LucaApplication>()
    private val whatIsNewManager by lazy { application.getInitializedManager(application.whatIsNewManager).blockingGet() }

    fun givenMessageEnabled(messageId: String, isEnabled: Boolean) {
        with(whatIsNewManager) {
            updateMessage(messageId) { copy(enabled = isEnabled) }
                .blockingAwait()
        }
    }
}
