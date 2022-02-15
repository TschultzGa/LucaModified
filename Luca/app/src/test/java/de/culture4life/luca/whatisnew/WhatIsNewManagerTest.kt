package de.culture4life.luca.whatisnew

import androidx.test.runner.AndroidJUnit4
import de.culture4life.luca.LucaUnitTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [28])
@RunWith(AndroidJUnit4::class)
class WhatIsNewManagerTest : LucaUnitTest() {

    private val whatIsNewManager = getInitializedManager(application.whatIsNewManager)

    @Test
    fun `Messages are correctly initiated`() {
        val messages = whatIsNewManager.getAllMessages().toList().blockingGet()
        assertFalse(messages.isEmpty())
        messages.forEach {
            with(it) {
                assertNotNull(id)
                assertNotNull(destination)
                assertNotNull(title)
                assertNotNull(content)
                assertTrue(timestamp > 0)
                assertFalse(notified)
                assertFalse(seen)
            }
        }
    }

    @Test
    fun `Marking message as notified updates notified property`() {
        val getFirstMessage = whatIsNewManager.getAllMessages().firstOrError()

        getFirstMessage
            .flatMapCompletable { whatIsNewManager.markMessageAsNotified(it.id!!) }
            .andThen(getFirstMessage)
            .map { it.notified }
            .test()
            .assertValue(true)
    }

    @Test
    fun `Marking message as seen updates seen property`() {
        val getFirstMessage = whatIsNewManager.getAllMessages().firstOrError()

        getFirstMessage
            .flatMapCompletable { whatIsNewManager.markMessageAsSeen(it.id!!) }
            .andThen(getFirstMessage)
            .map { it.seen }
            .test()
            .assertValue(true)
    }

}