package de.culture4life.luca.ui.messages

import androidx.annotation.IdRes
import de.culture4life.luca.R
import de.culture4life.luca.consent.ConsentManager
import de.culture4life.luca.testtools.LucaFragmentTest
import de.culture4life.luca.testtools.pages.MessagesPage
import de.culture4life.luca.testtools.preconditions.ConsentPreconditions
import de.culture4life.luca.testtools.preconditions.MockServerPreconditions
import de.culture4life.luca.testtools.rules.LucaFragmentScenarioRule
import de.culture4life.luca.util.isHttpException
import io.github.kakaocup.kakao.list.KAbsListView
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class MessagesFragmentLucaIdTest : LucaFragmentTest<MessagesFragment>(LucaFragmentScenarioRule.create()) {

    private val anyHttpError = 400

    @Before
    fun setup() {
        // hide messages not relevant for this test class
        ConsentPreconditions().givenConsent(ConsentManager.ID_TERMS_OF_SERVICE_LUCA_ID, true)

        with(mockServerPreconditions) {
            givenTimeSync()
            givenPowChallenge()
            givenLucaIdCreateEnrollment()
            givenLucaIdEnrollmentStatusQueued()
            givenLucaIdDelete()
            givenLucaIdDeleteIdent()
            givenAttestationNonce()
            givenAttestationRegister()
            givenAttestationAssert()
        }
    }

    @Test
    fun showEnrollmentTokenMessage() {
        fixWhatIsNewManagerNotInitialized()
        fragmentScenarioRule.launch(onCreated = { setupTestNavigationController(it, R.id.messagesFragment) })

        MessagesPage().run {
            messageList.run {
                assertShowsOnlyDefaultMessages()
                mockServerPreconditions.givenLucaIdEnrollmentStatusPending()
                whenEnrolled()
                waitFor(MessagesViewModel.WHAT_IS_NEW_MESSAGE_UPDATE_DELAY)
                hasSize(2)
                childAt<MessagesPage.MessageItem>(1) {
                    assertIsEnrollmentTokenMessage()
                    click()
                }
            }
        }
        assertNavigationDestination(R.id.lucaIdEnrollmentTokenFragment)
    }

    @Test
    fun showEnrollErrorMessage_fromEnrollError() {
        fixWhatIsNewManagerNotInitialized()
        fragmentScenarioRule.launch(onCreated = { setupTestNavigationController(it, R.id.messagesFragment) })

        // show message when failed
        MessagesPage().run {
            messageList.run {
                assertShowsOnlyDefaultMessages()
                whenEnrolled(anyHttpError)
                hasSize(2)
                childAt<MessagesPage.MessageItem>(1) {
                    assertIsEnrollmentErrorMessage()
                    click()
                }
            }
        }
        assertNavigationDestination(R.id.lucaIdEnrollmentErrorFragment)

        // hide message after success
        MessagesPage().run {
            messageList.run {
                mockServerPreconditions.givenLucaIdCreateEnrollment()
                mockServerPreconditions.givenLucaIdEnrollmentStatusPending()
                whenEnrolled()
                hasSize(2)
                childAt<MessagesPage.MessageItem>(1) {
                    assertIsEnrollmentTokenMessage()
                }
            }
        }
    }

    @Test
    fun showEnrollErrorMessage_fromStatusUpdate() {
        fixWhatIsNewManagerNotInitialized()
        fragmentScenarioRule.launch(onCreated = { setupTestNavigationController(it, R.id.messagesFragment) })

        // show message when failed
        MessagesPage().run {
            messageList.run {
                assertShowsOnlyDefaultMessages()
                mockServerPreconditions.givenLucaIdEnrollmentStatusFailed()
                whenEnrolled()
                hasSize(2)
                childAt<MessagesPage.MessageItem>(1) {
                    assertIsEnrollmentErrorMessage()
                    click()
                }
            }
        }
        assertNavigationDestination(R.id.lucaIdEnrollmentErrorFragment)
    }

    @Test
    fun resetEnrollmentMessagesAfterDelete() {
        fixWhatIsNewManagerNotInitialized()
        fragmentScenarioRule.launch(onCreated = { setupTestNavigationController(it, R.id.messagesFragment) })

        MessagesPage().messageList.run { assertShowsOnlyDefaultMessages() }

        mockServerPreconditions.givenLucaIdEnrollmentStatusSuccess()
        whenEnrolled()
        MessagesPage().messageList.run {
            hasSize(2)
            childAt<MessagesPage.MessageItem>(0) { assertIsPostalCodeMessage() }
            childAt<MessagesPage.MessageItem>(1) { assertIsLucaIdVerificationSuccessMessage() }
        }

        whenDeleted()
        MessagesPage().messageList.run { assertShowsOnlyDefaultMessages() }
    }

    @Test
    fun showVerificationSuccessMessage() {
        fixWhatIsNewManagerNotInitialized()
        mockServerPreconditions.givenLucaIdEnrollmentStatusPending()
        fragmentScenarioRule.launch(onCreated = { setupTestNavigationController(it, R.id.messagesFragment) })

        MessagesPage().run {
            messageList.run {
                assertShowsOnlyDefaultMessages()
                whenEnrolled()
                childAt<MessagesPage.MessageItem>(1) { assertIsEnrollmentTokenMessage() }

                mockServerPreconditions.givenLucaIdEnrollmentStatusSuccess()
                whenVerifyVerificationStatus()
                waitFor(MessagesViewModel.WHAT_IS_NEW_MESSAGE_UPDATE_DELAY)
                hasSize(3)
                childAt<MessagesPage.MessageItem>(2) { assertIsEnrollmentTokenMessage() }
                childAt<MessagesPage.MessageItem>(1) {
                    assertIsLucaIdVerificationSuccessMessage()
                    click()
                }
            }
        }
        assertNavigationDestination(R.id.lucaIdVerificationFragment)
    }

    private fun whenVerifyVerificationStatus() {
        application.idNowManager.updateEnrollmentStatus().blockingAwait()
    }

    private fun fixWhatIsNewManagerNotInitialized() {
        // Ensure WhatsNewManager is initialized before we reach MessageFragment onResume.
        //   Context.getString(int)' on a null object reference
        //   WhatIsNewManager.restoreOrCreatePostalCodeMessage$lambda-18(WhatIsNewManager.kt:288)
        // Bug or just not possible to get in that state before initialization is done?
        getInitializedManager(application.whatIsNewManager)
    }

    private fun KAbsListView.assertShowsOnlyDefaultMessages() {
        hasSize(1)
        childAt<MessagesPage.MessageItem>(0) {
            assertIsPostalCodeMessage()
        }
    }

    private fun whenEnrolled(expectError: Int? = null) {
        var enroll = getInitializedManager(application.idNowManager)
            .initiateEnrollment()

        if (expectError != null) {
            mockServerPreconditions.givenHttpError(MockServerPreconditions.Route.LucaIdCreateEnrollment, expectError)
            enroll = enroll.onErrorComplete { it.isHttpException(expectError) }
        }

        enroll.blockingAwait()
        waitFor(MessagesViewModel.WHAT_IS_NEW_MESSAGE_UPDATE_DELAY)
    }

    private fun whenDeleted() {
        application.idNowManager.unEnroll().blockingAwait()
        waitFor(MessagesViewModel.WHAT_IS_NEW_MESSAGE_UPDATE_DELAY)
    }

    private fun assertNavigationDestination(@IdRes navigationId: Int) {
        Assert.assertEquals(
            application.resources.getResourceName(navigationId),
            testNavigationController.currentDestination!!.displayName
        )
    }
}
