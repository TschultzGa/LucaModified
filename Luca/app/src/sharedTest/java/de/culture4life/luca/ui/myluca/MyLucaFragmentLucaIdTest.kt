package de.culture4life.luca.ui.myluca

import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import de.culture4life.luca.R
import de.culture4life.luca.idnow.IdNowManager
import de.culture4life.luca.idnow.LucaIdData
import de.culture4life.luca.registration.RegistrationData
import de.culture4life.luca.registration.RegistrationManager
import de.culture4life.luca.testtools.LucaFragmentTest
import de.culture4life.luca.testtools.mocks.IntentMocks
import de.culture4life.luca.testtools.pages.MyLucaPage
import de.culture4life.luca.testtools.preconditions.MockServerPreconditions.Route
import de.culture4life.luca.testtools.rules.FixedTimeRule
import de.culture4life.luca.testtools.rules.LucaFragmentScenarioRule
import de.culture4life.luca.testtools.samples.SampleDocuments
import io.github.kakaocup.kakao.recycler.KRecyclerView
import org.hamcrest.Matchers
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

class MyLucaFragmentLucaIdTest : LucaFragmentTest<MyLucaFragment>(LucaFragmentScenarioRule.create()) {

    @get:Rule
    val fixedTimeRule = FixedTimeRule()

    @Before
    fun setup() {
        with(mockServerPreconditions) {
            givenTimeSync()
            givenRedeemDocument()
            givenLucaIdCreateEnrollment()
            givenLucaIdEnrollmentStatusPending()
            givenAttestationNonce()
            givenAttestationRegister()
            givenAttestationAssert()
            givenLucaIdDelete()
            givenLucaIdDeleteIdent()
        }

        getInitializedManager(application.consentManager)
    }

    @Test
    fun performIdNowEnrollment() {
        givenRegisteredUser("Erika", "Mustermann")
        IntentMocks.givenMarketIntentResponse()
        fragmentScenarioRule.launch()

        MyLucaPage().run {
            documentList.hasSize(2)
            documentList.childAt<MyLucaPage.CreateIdentityItem>(1) { click() }
            addIdentityFlow.run {
                explanationPage.isDisplayed()
                explanationPage.actionButton.scrollTo()
                explanationPage.actionButton.click()

                consentPage.isDisplayed()
                consentPage.actionButton.scrollTo()
                consentPage.actionButton.click()
                consentPage.authenticationNotActivatedDialog.continueButton.click()
                consentPage.authenticationDialog.okButton.click() // TODO: fails on emulator (Pixel 3a API 31 x86_64)

                successPage.isDisplayed()

                // TODO: remove assertions or be less strict about order
                mockServerPreconditions.assert(Route.AttestationNonce)
                mockServerPreconditions.assert(Route.AttestationNonce)
                mockServerPreconditions.assert(Route.AttestationRegister)
                mockServerPreconditions.assert(Route.AttestationAssert)
                mockServerPreconditions.assert(Route.AttestationNonce)
                mockServerPreconditions.assert(Route.AttestationNonce)
                mockServerPreconditions.assert(Route.LucaIdCreateEnrollment)
                mockServerPreconditions.assert(Route.LucaIdEnrollmentStatus)

                // does close and trigger the open IDnow intent
                successPage.actionButton.scrollTo()
                successPage.actionButton.click()

                // would redirect to play store
                Intents.intended(Matchers.allOf(IntentMatchers.hasData(IdNowManager.ID_NOW_PLAY_STORE_URI)))
            }
        }
    }

    @Test
    fun identCardChanges() {
        givenRegisteredUser("Erika", "Mustermann")
        fragmentScenarioRule.launch()

        MyLucaPage().run {
            documentList.hasSize(2)
            documentList.assertShowsOnlyDefaultEntries()
            documentList.assertIsCreateLucaIdItemOnPosition(1)

            mockServerPreconditions.givenLucaIdEnrollmentStatusQueued()
            whenCreateEnrollment()

            // TODO no documents added item should also be shown, from design at least for QUEUED.
            //  I expect that should be for all cases.
            documentList.hasSize(1)
            documentList.assertIsIdentityRequestQueuedItemOnPosition(0)

            mockServerPreconditions.givenLucaIdEnrollmentStatusPending()
            whenUpdateEnrollmentStatus()
            documentList.hasSize(1)
            documentList.assertIsIdentityRequestedItemOnPosition(0)

            mockServerPreconditions.givenLucaIdEnrollmentStatusSuccess()
            whenUpdateEnrollmentStatus()
            documentList.hasSize(1)
            documentList.assertIsIdentityItemOnPosition(0)

            whenDeleteIdent()
            documentList.assertShowsOnlyDefaultEntries()
        }
    }

    @Test
    fun pendingIdentCardIsAborted() {
        givenRegisteredUser("Erika", "Mustermann")
        fragmentScenarioRule.launch()

        MyLucaPage().run {
            mockServerPreconditions.givenLucaIdEnrollmentStatusPending()
            whenUpdateEnrollmentStatus()
            documentList.hasSize(1)
            documentList.assertIsIdentityRequestedItemOnPosition(0)

            documentList.childAt<MyLucaPage.IdentityRequestedItem>(0) { longClick() }
            deleteDialog.okButton.click()

            documentList.assertShowsOnlyDefaultEntries()
        }
    }

    @Test
    fun scanVaccinationAndAddIdentity() {
        val document = SampleDocuments.ErikaMustermann.EudccFullyVaccinated()
        givenRegisteredUser(document.person)
        addMinimalIdentity(SampleDocuments.ErikaMustermann())
        fragmentScenarioRule.launch()
        initializeConsentUiExtension()

        MyLucaPage().run {
            documentList.hasSize(1)
            stepsScanValidDocument(fragmentScenarioRule.scenario, document)
            documentList.run {
                hasSize(2)
                childAt<MyLucaPage.DocumentItem>(0) { title.hasText(application.getString(R.string.certificate_type_vaccination, "(2/2)")) }
                childAt<MyLucaPage.IdentityItem>(1) { name.hasText(R.string.luca_id_card_name_blurry_placeholder) }
            }
        }
    }

    @Test
    @Ignore("Not implemented")
    fun testIdentityRemoval() {
        // TODO Should be implemented after we have a test that allows opening of the ID card
    }

    private fun addMinimalIdentity(person: SampleDocuments.Person) {
        application.preferencesManager.persist(
            RegistrationManager.REGISTRATION_DATA_KEY,
            RegistrationData(firstName = person.firstName, lastName = person.lastName)
        ).blockingAwait()
        application.idNowManager.persistLucaIdData(
            LucaIdData(
                revocationCode = "revocationCode",
                enrollmentToken = "enrollmentToken",
                verificationStatus = LucaIdData.VerificationStatus.SUCCESS,
            )
        ).blockingAwait()
    }

    private fun givenRegisteredUser(person: SampleDocuments.Person) = givenRegisteredUser(person.firstName, person.lastName)

    private fun givenRegisteredUser(firstName: String, lastName: String) {
        // Transitive used PreferencesManager needs to be initialized before we can fake registered user.
        getInitializedManager(application.preferencesManager)

        val registrationManager = application.registrationManager
        val registrationData = registrationManager.getRegistrationData().blockingGet()

        registrationData.firstName = firstName
        registrationData.lastName = lastName

        registrationManager.persistRegistrationData(registrationData).blockingAwait()
    }

    private fun KRecyclerView.assertShowsOnlyDefaultEntries() {
        hasSize(2)
        childAt<MyLucaPage.NoDocumentsItem>(0) {
            // TODO no documents info item
        }
        childAt<MyLucaPage.CreateIdentityItem>(1) {
            assertIsExpectedItemType()
        }
    }

    private fun whenCreateEnrollment() {
        application.idNowManager.initiateEnrollment().blockingAwait()
        // TODO remove after status observation is implemented
        //  Scenario recreate does not wait for IdlingResources what leads to an "old" state when too fast.
        waitForIdle()
    }

    private fun whenUpdateEnrollmentStatus() {
        application.idNowManager.updateEnrollmentStatus().blockingAwait()
    }

    private fun whenDeleteIdent() {
        application.idNowManager.unEnroll().blockingAwait()
    }
}
