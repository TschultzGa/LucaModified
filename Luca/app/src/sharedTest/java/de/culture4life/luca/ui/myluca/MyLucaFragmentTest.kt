package de.culture4life.luca.ui.myluca

import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import de.culture4life.luca.R
import de.culture4life.luca.children.Child
import de.culture4life.luca.testtools.LucaFragmentTest
import de.culture4life.luca.testtools.pages.MyLucaPage
import de.culture4life.luca.testtools.preconditions.DocumentPreconditions
import de.culture4life.luca.testtools.preconditions.MockServerPreconditions
import de.culture4life.luca.testtools.rules.FixedTimeRule
import de.culture4life.luca.testtools.rules.LucaFragmentScenarioRule
import de.culture4life.luca.testtools.samples.SampleDocuments
import de.culture4life.luca.testtools.samples.SampleLocations
import de.culture4life.luca.ui.BaseQrCodeViewModel
import io.github.kakaocup.kakao.recycler.KRecyclerView
import org.joda.time.DateTime
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MyLucaFragmentTest : LucaFragmentTest<MyLucaFragment>(LucaFragmentScenarioRule.create()) {

    @get:Rule
    val fixedTimeRule = FixedTimeRule()

    val documentPreconditions = DocumentPreconditions()

    @Before
    fun setup() {
        setupDefaultWebServerMockResponses()

        // Transitive used manager needs to be initialized before start.
        // A bug or just not possible, because usually initialization is already done before reaching MyLucaFragment?
        getInitializedManager(application.consentManager)
        // NotificationManager is used to give feedback as vibration.
        // Vibration is not added to AddCertificateFlowPage scan process yet but will be.
        getInitializedManager(application.notificationManager)
    }

    @Test
    fun scanEudccFullyVaccinated() {
        val document = SampleDocuments.ErikaMustermann.EudccFullyVaccinated()
        givenRegisteredUser(document.person)
        launchFragment()

        MyLucaPage().run {
            documentList.assertShowsOnlyDefaultEntries()
            stepsScanValidDocument(fragmentScenarioRule.scenario, document)
            documentList.run {
                hasSize(2)
                childAt<MyLucaPage.DocumentItem>(0) {
                    title.hasText(application.getString(R.string.certificate_type_vaccination, "(2/2)"))
                    assertIsMarkedAsValid()
                }
            }
        }

        assertDocumentRedeemRequest()

        givenFarFuture(document.vaccinationDate)
        MyLucaPage().documentList.childAt<MyLucaPage.DocumentItem>(0) {
            title.hasText(application.getString(R.string.certificate_type_vaccination, "(2/2)"))
            assertIsMarkedAsExpired()
        }
    }

    @Test
    fun scanEudccPartiallyVaccinated() {
        val document = SampleDocuments.ErikaMustermann.EudccPartiallyVaccinated()
        givenRegisteredUser(document.person)
        launchFragment()

        MyLucaPage().run {
            documentList.assertShowsOnlyDefaultEntries()
            stepsScanValidDocument(fragmentScenarioRule.scenario, document)
            documentList.run {
                hasSize(2)
                childAt<MyLucaPage.DocumentItem>(0) {
                    title.hasText(application.getString(R.string.certificate_type_vaccination, "(1/2)"))
                    assertIsMarkedAsPartially()
                }
            }
        }

        assertDocumentRedeemRequest()

        givenFarFuture(document.vaccinationDate)
        MyLucaPage().documentList.childAt<MyLucaPage.DocumentItem>(0) {
            title.hasText(application.getString(R.string.certificate_type_vaccination, "(1/2)"))
            assertIsMarkedAsExpired()
        }
    }

    @Test
    fun scanEudccRecovered() {
        val document = SampleDocuments.ErikaMustermann.EudccRecovered()
        givenRegisteredUser(document.person)
        launchFragment()

        MyLucaPage().run {
            documentList.assertShowsOnlyDefaultEntries()
            stepsScanValidDocument(fragmentScenarioRule.scenario, document)
            documentList.run {
                hasSize(2)
                childAt<MyLucaPage.DocumentItem>(0) {
                    title.hasText(R.string.certificate_type_recovery)
                    assertIsMarkedAsValid()
                }
            }
        }

        assertDocumentRedeemRequest()

        givenFarFuture(document.startDate)
        MyLucaPage().documentList.childAt<MyLucaPage.DocumentItem>(0) {
            title.hasText(R.string.certificate_type_recovery)
            assertIsMarkedAsExpired()
        }
    }

    @Test
    fun scanEudccTestFastNegative() {
        val document = SampleDocuments.ErikaMustermann.EudccFastNegative()
        givenRegisteredUser(document.person)
        launchFragment()

        MyLucaPage().run {
            documentList.assertShowsOnlyDefaultEntries()
            stepsScanValidDocument(fragmentScenarioRule.scenario, document)
            documentList.run {
                hasSize(2)
                childAt<MyLucaPage.DocumentItem>(0) {
                    title.containsText(application.getString(R.string.certificate_type_test_fast) + ": " + application.getString(R.string.certificate_test_outcome_negative))
                    assertIsMarkedAsNegative()
                }
            }
        }

        assertDocumentRedeemRequest()

        givenFarFuture(document.testingDateTime)
        MyLucaPage().documentList.assertShowsOnlyDefaultEntries()
    }

    @Test
    fun scanEudccTestPcrNegative() {
        val document = SampleDocuments.ErikaMustermann.EudccPcrNegative()
        givenRegisteredUser(document.person)
        launchFragment()

        MyLucaPage().run {
            documentList.assertShowsOnlyDefaultEntries()
            stepsScanValidDocument(fragmentScenarioRule.scenario, document)
            documentList.run {
                hasSize(2)
                childAt<MyLucaPage.DocumentItem>(0) {
                    title.containsText(application.getString(R.string.certificate_type_test_pcr) + ": " + application.getString(R.string.certificate_test_outcome_negative))
                    assertIsMarkedAsNegative()
                }
            }
        }

        assertDocumentRedeemRequest()

        givenFarFuture(document.testingDateTime)
        MyLucaPage().documentList.assertShowsOnlyDefaultEntries()
    }

    @Test
    fun scanEudccTestPcrPositive() {
        val document = SampleDocuments.ErikaMustermann.EudccPcrPositive()
        givenRegisteredUser(document.person)
        launchFragment()

        MyLucaPage().run {
            documentList.assertShowsOnlyDefaultEntries()
            stepsScanValidDocument(fragmentScenarioRule.scenario, document)
            documentList.run {
                hasSize(2)
                childAt<MyLucaPage.DocumentItem>(0) {
                    title.containsText(application.getString(R.string.certificate_type_test_pcr) + ": " + application.getString(R.string.certificate_test_outcome_positive))
                    assertIsMarkedAsPositive()
                }
            }
        }

        assertDocumentRedeemRequest()

        givenFarFuture(document.testingDateTime)
        MyLucaPage().documentList.childAt<MyLucaPage.DocumentItem>(0) {
            title.containsText(application.getString(R.string.certificate_type_test_pcr) + ": " + application.getString(R.string.certificate_test_outcome_positive))
            assertIsMarkedAsExpired()
        }
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
                hasSize(2)
                childAt<MyLucaPage.DocumentItem>(0) {
                    title.hasText(application.getString(R.string.certificate_type_vaccination, "(2/2)"))
                    assertIsMarkedAsValid()
                }
            }
        }

        assertDocumentRedeemRequest()

        givenFarFuture(document.vaccinationDate)
        MyLucaPage().documentList.childAt<MyLucaPage.DocumentItem>(0) {
            title.hasText(application.getString(R.string.certificate_type_vaccination, "(2/2)"))
            assertIsMarkedAsExpired()
        }
    }

    private fun KRecyclerView.assertShowsOnlyDefaultEntries() {
        hasSize(2)
        childAt<MyLucaPage.NoDocumentsItem>(0) {
            // TODO no documents info item
        }
        childAt<MyLucaPage.CreateIdentityItem>(1) {
            // TODO create luca id info item
        }
    }

    @Test
    fun scanFullyVaccinatedPersonWithFullyVaccinatedChild() {
        val parentDocument = SampleDocuments.ErikaMustermann.EudccFullyVaccinated()
        val childDocument = SampleDocuments.JulianMusterkind.EudccFullyVaccinated()
        givenRegisteredUser(parentDocument.person)
        givenAddedChild(childDocument.person)

        launchFragment()

        MyLucaPage().run {
            documentList.assertShowsOnlyDefaultEntries()
            stepsScanValidDocument(fragmentScenarioRule.scenario, parentDocument)
            stepsScanValidDocument(fragmentScenarioRule.scenario, childDocument)
            documentList.run {
                hasSize(4)
                childAt<MyLucaPage.DocumentItem>(0) {
                    title.hasText(application.getString(R.string.certificate_type_vaccination, "(2/2)"))
                    assertIsMarkedAsValid()
                }
                // pos 1 is id card
                // pos 2 is child title
                childAt<MyLucaPage.DocumentItem>(3) {
                    title.hasText(application.getString(R.string.certificate_type_vaccination, "(2/2)"))
                    assertIsMarkedAsValid()
                }
            }
        }

        givenFarFuture(parentDocument.vaccinationDate)
        MyLucaPage().documentList.run {
            childAt<MyLucaPage.DocumentItem>(0) {
                title.hasText(application.getString(R.string.certificate_type_vaccination, "(2/2)"))
                assertIsMarkedAsExpired()
            }
            childAt<MyLucaPage.DocumentItem>(3) {
                title.hasText(application.getString(R.string.certificate_type_vaccination, "(2/2)"))
                assertIsMarkedAsExpired()
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
            documentList.assertShowsOnlyDefaultEntries()
            stepsScanValidDocument(fragmentScenarioRule.scenario, documentFirst)
            stepsScanValidDocument(fragmentScenarioRule.scenario, documentSecond)
            stepsScanValidDocument(fragmentScenarioRule.scenario, documentThird)
            documentList.run {
                hasSize(2)
                // TODO check it shows the most recent scanned document
                // TODO check item contains all documents
            }
        }

        // Once for every document
        assertDocumentRedeemRequest()
        assertDocumentRedeemRequest()
        assertDocumentRedeemRequest()
    }

    @Test
    fun redirectBirthdayNotMatch() {
        val documentFirst = SampleDocuments.ErikaMustermann.EudccPartiallyVaccinated()
        val documentSecond = SampleDocuments.ErikaMustermannDifferentBirthday.EudccFullyVaccinated()
        givenRegisteredUser(documentFirst.person)
        documentPreconditions.givenAddedDocument(documentFirst)

        launchFragmentSimulatedRedirect(documentSecond)

        MyLucaPage().run {
            consentsDialog.acceptButton.click()
            birthdayNotMatchDialog.okButton.click()
            documentList.hasSize(2)
        }
    }

    @Test
    fun scanBirthdayNotMatch() {
        val documentFirst = SampleDocuments.ErikaMustermann.EudccPartiallyVaccinated()
        val documentSecond = SampleDocuments.ErikaMustermannDifferentBirthday.EudccFullyVaccinated()
        givenRegisteredUser(documentFirst.person)
        launchFragment()

        MyLucaPage().run {
            documentList.assertShowsOnlyDefaultEntries()
            stepsScanValidDocument(fragmentScenarioRule.scenario, documentFirst)
            documentList.hasSize(2)
            navigateToScanner()
            addCertificateFlowPage.qrCodeScannerPage.run {
                scanQrCode(fragmentScenarioRule.scenario, documentSecond.qrCodeContent)
                consentsDialog.acceptButton.click()
                birthdayNotMatchDialog.okButton.click()
                cancelButton.click()
            }
            documentList.hasSize(2)
        }
    }

    @Test
    fun cardStaysExpandedOnPauseAndResume() {
        val document = SampleDocuments.ErikaMustermann.EudccFullyVaccinated()
        givenRegisteredUser(document.person)
        documentPreconditions.givenAddedDocument(document)
        launchFragment()

        MyLucaPage().documentList.childAt<MyLucaPage.DocumentItem>(0) {
            assertIsExpectedItemType()

            assertIsCollapsed()
            click()
            assertIsExpanded()

            fragmentScenarioRule.scenario.moveToState(Lifecycle.State.STARTED)
            fragmentScenarioRule.scenario.moveToState(Lifecycle.State.RESUMED)

            assertIsExpanded()
        }
    }

    private fun launchFragment() {
        fragmentScenarioRule.launch()
        initializeConsentUiExtension()
    }

    private fun launchFragmentSimulatedRedirect(document: SampleDocuments) {
        fragmentScenarioRule.launchSimulateRedirect(
            bundleOf(BaseQrCodeViewModel.BARCODE_DATA_KEY to document.qrCodeContent)
        ) {
            initializeConsentUiExtension()
        }
    }

    private fun assertDocumentRedeemRequest() {
        mockServerPreconditions.assert(MockServerPreconditions.Route.RedeemDocument)
    }

    private fun setupDefaultWebServerMockResponses() {
        with(mockServerPreconditions) {
            givenTimeSync()
            givenRedeemDocument()
        }
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

    private fun givenAddedChild(person: SampleDocuments.Person) = givenAddedChild(person.firstName, person.lastName)
    private fun givenAddedChild(firstName: String, lastName: String) {
        val childrenManager = application.childrenManager
        childrenManager.addChild(Child(firstName, lastName)).blockingAwait()
    }

    private fun givenFarFuture(start: DateTime) {
        fixedTimeRule.setCurrentDateTime(start.plusYears(100))
        // Ensure we don't test entries which becomes deleted and triggers list update.
        application.documentManager.deleteExpiredDocuments().blockingAwait()
    }
}
