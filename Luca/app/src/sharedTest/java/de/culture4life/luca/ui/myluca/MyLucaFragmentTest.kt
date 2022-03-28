package de.culture4life.luca.ui.myluca

import androidx.core.os.bundleOf
import androidx.test.espresso.Espresso
import de.culture4life.luca.R
import de.culture4life.luca.children.Child
import de.culture4life.luca.testtools.LucaFragmentTest
import de.culture4life.luca.testtools.pages.MyLucaPage
import de.culture4life.luca.testtools.rules.LucaFragmentScenarioRule
import de.culture4life.luca.testtools.samples.SampleDocuments
import de.culture4life.luca.testtools.samples.SampleLocations
import de.culture4life.luca.ui.BaseQrCodeViewModel
import de.culture4life.luca.ui.consent.ConsentUiExtension
import de.culture4life.luca.util.TimeUtil
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId

class MyLucaFragmentTest : LucaFragmentTest<MyLucaFragment>(LucaFragmentScenarioRule.create()) {

    @Before
    fun setup() {
        // TODO replace with configurable TestRule
        val fixedDateTime = LocalDateTime.parse(SampleDocuments.referenceDateTime)
        TimeUtil.clock = Clock.fixed(fixedDateTime.atZone(ZoneId.of("UTC")).toInstant(), ZoneId.of("UTC"))

        setupDefaultWebServerMockResponses()

        // Transitive used manager needs to be initialized before start.
        // A bug or just not possible, because usually initialization is already done before reaching MyLucaFragment?
        getInitializedManager(applicationContext.consentManager)
        // NotificationManager is used to give feedback as vibration.
        // Vibration is not added to AddCertificateFlowPage scan process yet but will be.
        getInitializedManager(applicationContext.notificationManager)
    }

    @After
    fun cleanup() {
        // TODO replace reset by TestRule
        TimeUtil.clock = Clock.systemUTC()
    }

    @Test
    fun scanEudccFullyVaccinated() {
        val document = SampleDocuments.ErikaMustermann.EudccFullyVaccinated()
        givenRegisteredUser(document.person)
        launchFragment()

        MyLucaPage().run {
            documentList.hasSize(0)
            stepsScanValidDocument(fragmentScenarioRule.scenario, document)
            documentList.run {
                hasSize(1)
                childAt<MyLucaPage.DocumentItem>(0) {
                    title.hasText(applicationContext.getString(R.string.document_type_vaccination, "(2/2)"))
                }
            }
        }

        assertDocumentRedeemRequest()
    }

    @Test
    fun scanEudccPartiallyVaccinated() {
        val document = SampleDocuments.ErikaMustermann.EudccPartiallyVaccinated()
        givenRegisteredUser(document.person)
        launchFragment()

        MyLucaPage().run {
            documentList.hasSize(0)
            stepsScanValidDocument(fragmentScenarioRule.scenario, document)
            documentList.run {
                hasSize(1)
                childAt<MyLucaPage.DocumentItem>(0) {
                    title.hasText(applicationContext.getString(R.string.document_type_vaccination, "(1/2)"))
                }
            }
        }

        assertDocumentRedeemRequest()
    }

    @Test
    fun scanEudccRecovered() {
        val document = SampleDocuments.ErikaMustermann.EudccRecovered()
        givenRegisteredUser(document.person)
        launchFragment()

        MyLucaPage().run {
            documentList.hasSize(0)
            stepsScanValidDocument(fragmentScenarioRule.scenario, document)
            documentList.run {
                hasSize(1)
                childAt<MyLucaPage.DocumentItem>(0) {
                    title.hasText(R.string.document_type_recovery)
                }
            }
        }

        assertDocumentRedeemRequest()
    }

    @Test
    fun scanEudccTestFastNegative() {
        val document = SampleDocuments.ErikaMustermann.EudccPcrNegative()
        givenRegisteredUser(document.person)
        launchFragment()

        MyLucaPage().run {
            documentList.hasSize(0)
            stepsScanValidDocument(fragmentScenarioRule.scenario, document)
            documentList.run {
                hasSize(1)
                childAt<MyLucaPage.DocumentItem>(0) {
                    title.containsText(applicationContext.getString(R.string.document_type_fast) + ": " + applicationContext.getString(R.string.document_outcome_negative))
                }
            }
        }

        assertDocumentRedeemRequest()
    }

    @Test
    fun scanCheckIn() {
        givenRegisteredUser("doesn't", "matter")
        launchFragment()

        MyLucaPage().run {
            navigateToScanner()
            addCertificateFlowPage.qrCodeScannerPage.run {
                scanQrCode(fragmentScenarioRule.scenario, SampleLocations.CheckIn.Valid().qrCodeContent)
                checkInNotSupportedDialog.okButton.click()
                isDisplayed() // Should just stay on qr code scanner now.
            }
        }
    }

    @Test
    fun scanMeeting() {
        givenRegisteredUser("doesn't", "matter")
        launchFragment()

        MyLucaPage().run {
            navigateToScanner()
            addCertificateFlowPage.qrCodeScannerPage.run {
                scanQrCode(fragmentScenarioRule.scenario, SampleLocations.Meeting.Valid().qrCodeContent)
                checkInNotSupportedDialog.okButton.click()
                isDisplayed() // Should just stay on qr code scanner now.
            }
        }
    }

    @Test
    fun redirectWithScannedDocument() {
        val document = SampleDocuments.ErikaMustermann.EudccFullyVaccinated()
        givenRegisteredUser(document.person)

        launchFragmentSimulatedRedirect(document)

        MyLucaPage().run {
            consentsDialog.acceptButton.click()
            documentList.run {
                hasSize(1)
                childAt<MyLucaPage.DocumentItem>(0) {
                    title.hasText(applicationContext.getString(R.string.document_type_vaccination, "(2/2)"))
                }
            }
        }

        assertDocumentRedeemRequest()
    }

    @Test
    fun scanFullyVaccinatedPersonWithFullyVaccinatedChild() {
        val parentDocument = SampleDocuments.ErikaMustermann.EudccFullyVaccinated()
        val childDocument = SampleDocuments.JulianMusterkind.EudccFullyVaccinated()
        givenRegisteredUser(parentDocument.person)
        givenAddedChild(childDocument.person)

        launchFragment()

        MyLucaPage().run {
            documentList.hasSize(0)
            stepsScanValidDocument(fragmentScenarioRule.scenario, parentDocument)
            stepsScanValidDocument(fragmentScenarioRule.scenario, childDocument)
            documentList.run {
                hasSize(3)
                childAt<MyLucaPage.DocumentItem>(0) {
                    title.hasText(applicationContext.getString(R.string.document_type_vaccination, "(2/2)"))
                }
                childAt<MyLucaPage.DocumentItem>(2) {
                    title.hasText(applicationContext.getString(R.string.document_type_vaccination, "(2/2)"))
                }
            }
        }
    }

    @Test
    fun scanMultipleVaccinations() {
        val documentFirst = SampleDocuments.ErikaMustermann.EudccPartiallyVaccinated()
        val documentSecond = SampleDocuments.ErikaMustermann.EudccFullyVaccinated()
        val documentThird = SampleDocuments.ErikaMustermann.EudccBoosteredVaccinated()
        givenRegisteredUser(documentFirst.person)
        launchFragment()

        MyLucaPage().run {
            documentList.hasSize(0)
            stepsScanValidDocument(fragmentScenarioRule.scenario, documentFirst)
            stepsScanValidDocument(fragmentScenarioRule.scenario, documentSecond)
            stepsScanValidDocument(fragmentScenarioRule.scenario, documentThird)
            documentList.run {
                hasSize(1)
                // TODO check it shows the most recent scanned document
                // TODO check item contains all documents
            }
        }

        // Once for every document
        assertDocumentRedeemRequest()
        assertDocumentRedeemRequest()

        // TODO Will be triggered after 3 seconds again which could be between the redeem calls
        //  .delaySubscription(3, TimeUnit.SECONDS, Schedulers.io())
        //  We need to improve how to check that our expected server calls are done and ignoring the common requests.
        // assertTimeSyncRequest()

        // TODO Is flaky at the moment
        // assertDocumentRedeemRequest()
    }

    @Test
    fun redirectBirthdayNotMatch() {
        val documentFirst = SampleDocuments.ErikaMustermann.EudccPartiallyVaccinated()
        val documentSecond = SampleDocuments.ErikaMustermannDifferentBirthday.EudccFullyVaccinated()
        givenRegisteredUser(documentFirst.person)
        givenAddedDocument(documentFirst)

        launchFragmentSimulatedRedirect(documentSecond)

        MyLucaPage().run {
            consentsDialog.acceptButton.click()
            birthdayNotMatchDialog.okButton.click()
            documentList.hasSize(1)
        }
    }

    @Test
    fun scanBirthdayNotMatch() {
        val documentFirst = SampleDocuments.ErikaMustermann.EudccPartiallyVaccinated()
        val documentSecond = SampleDocuments.ErikaMustermannDifferentBirthday.EudccFullyVaccinated()
        givenRegisteredUser(documentFirst.person)
        launchFragment()

        MyLucaPage().run {
            documentList.hasSize(0)
            stepsScanValidDocument(fragmentScenarioRule.scenario, documentFirst)
            documentList.hasSize(1)
            navigateToScanner()
            addCertificateFlowPage.qrCodeScannerPage.run {
                scanQrCode(fragmentScenarioRule.scenario, documentSecond.qrCodeContent)
                consentsDialog.acceptButton.click()
                birthdayNotMatchDialog.okButton.click()
                cancelButton.click()
            }
            documentList.hasSize(1)
        }
    }

    private fun launchFragment() {
        fragmentScenarioRule.launch()
        initializeConsentUiExtension()
        assertSyncRequests()
        try {
            // TODO: flaky; sometimes both requests are made, sometimes not; should be fixed by better logic in 'id' branch
            assertSyncRequests()
        } catch (e: IllegalStateException) {
            // ignore missing second call for now
        }
    }

    private fun launchFragmentSimulatedRedirect(document: SampleDocuments) {
        fragmentScenarioRule.launchSimulateRedirect(
            bundleOf(BaseQrCodeViewModel.BARCODE_DATA_KEY to document.qrCodeContent)
        ) {
            initializeConsentUiExtension()
        }
        assertSyncRequests()
    }

    private fun initializeConsentUiExtension() {
        fragmentScenarioRule.scenario.onFragment {
            ConsentUiExtension(it.childFragmentManager, applicationContext.consentManager, testDisposable)
        }
    }

    private fun assertSyncRequests() {
        // TODO:  Adjusted to two paths due to race condition; better fix already exists on different branch
        // Ensure initialization and following request are done.
        Espresso.onIdle()
        // Request done on fragment start through manager initialization process. Check is here to remove that call
        // from the request queue to be able to assert expected calls for the current tested use case only.
        mockWebServerRule.assertGetRequest("/api/v3/timesync", "/api/v3/versions/apps/android")
    }

    private fun assertDocumentRedeemRequest() {
        mockWebServerRule.assertPostRequest("/api/v3/tests/redeem")
    }

    private fun setupDefaultWebServerMockResponses() {
        mockWebServerRule.mockResponse.apply {
            put("/api/v3/timesync") {
                setResponseCode(200)
                setBody("{\"unix\":${TimeUtil.getCurrentUnixTimestamp().blockingGet()}}")
            }
            put("/api/v3/tests/redeem") { setResponseCode(200) }
            put("/api/v3/versions/apps/android") { setResponseCode(200) }
        }
    }

    private fun givenRegisteredUser(person: SampleDocuments.Person) = givenRegisteredUser(person.firstName, person.lastName)

    private fun givenRegisteredUser(firstName: String, lastName: String) {
        // Transitive used PreferencesManager needs to be initialized before we can fake registered user.
        getInitializedManager(applicationContext.preferencesManager)

        val registrationManager = applicationContext.registrationManager
        val registrationData = registrationManager.getRegistrationData().blockingGet()

        registrationData.firstName = firstName
        registrationData.lastName = lastName

        registrationManager.persistRegistrationData(registrationData).blockingAwait()
    }

    private fun givenAddedDocument(document: SampleDocuments) {
        getInitializedManager(applicationContext.documentManager)
        applicationContext.documentManager.parseAndValidateEncodedDocument(document.qrCodeContent)
            .flatMapCompletable { applicationContext.documentManager.addDocument(it) }
            .blockingAwait()
    }

    private fun givenAddedChild(person: SampleDocuments.Person) = givenAddedChild(person.firstName, person.lastName)
    private fun givenAddedChild(firstName: String, lastName: String) {
        val childrenManager = applicationContext.childrenManager
        childrenManager.addChild(Child(firstName, lastName)).blockingAwait()
    }
}
