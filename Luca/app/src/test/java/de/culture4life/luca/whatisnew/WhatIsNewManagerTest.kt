package de.culture4life.luca.whatisnew

import androidx.test.runner.AndroidJUnit4
import de.culture4life.luca.LucaUnitTest
import de.culture4life.luca.testtools.rxjava.SubscriptionRecorder
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.doAnswer
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config

@Config(sdk = [28])
@RunWith(AndroidJUnit4::class)
class WhatIsNewManagerTest : LucaUnitTest() {

    private val notificationManagerSpy = spy(application.notificationManager)
    private val registrationManagerSpy = spy(application.registrationManager)
    private val whatIsNewManager = spy(
        getInitializedManager(
            WhatIsNewManager(application.preferencesManager, notificationManagerSpy, registrationManagerSpy)
        )
    )

    @After
    fun cleanup() {
        notificationManagerSpy.dispose()
        registrationManagerSpy.dispose()
        whatIsNewManager.dispose()
    }

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

    @Test
    fun `No notifications displayed after app update without successful registration`() {
        fixtureNotificationsAfterAppUpdate(
            lastAppVersionCode = 1,
            hasRegistration = false,
            triggersNotifications = false
        )

        // still no notifications when registration is done after update
        fixtureNotificationsAfterAppUpdate(
            lastAppVersionCode = whatIsNewManager.restoreLastUsedVersionNumberIfAvailable().blockingGet()!!,
            hasRegistration = true,
            triggersNotifications = false
        )
    }

    @Test
    fun `Notifications displayed after app update with successful registration`() {
        fixtureNotificationsAfterAppUpdate(
            lastAppVersionCode = 1,
            hasRegistration = true,
            triggersNotifications = true
        )
    }

    private fun fixtureNotificationsAfterAppUpdate(lastAppVersionCode: Int, hasRegistration: Boolean, triggersNotifications: Boolean) {
        // have some NewsMessages
        val sampleMessage = WhatIsNewMessage(id = "any", enabled = true)
        val messages = listOf(sampleMessage, sampleMessage, sampleMessage)
        doAnswer { Observable.fromIterable(messages) }.whenever(whatIsNewManager).getAllMessages()

        // force specific last version
        whatIsNewManager.persistLastUsedVersionNumber(lastAppVersionCode).blockingAwait()

        // set expected registration state
        doReturn(Single.just(hasRegistration)).whenever(registrationManagerSpy).hasCompletedRegistration()

        val subscriptionRecorder = SubscriptionRecorder { Completable.complete() }
        doAnswer(subscriptionRecorder).whenever(notificationManagerSpy).showNewsMessageNotification(any())

        // check has to be called to set required states before showNotification
        whatIsNewManager.checkAndUpdateLastUsedVersionNumber().blockingAwait()
        whatIsNewManager.showNotificationsForUnseenMessagesIfRequired().blockingAwait()

        // assert amount of notifications done
        val expectedNotificationCount = if (triggersNotifications) messages.size else 0
        subscriptionRecorder.verifySubscriptions(expectedNotificationCount)
    }
}
