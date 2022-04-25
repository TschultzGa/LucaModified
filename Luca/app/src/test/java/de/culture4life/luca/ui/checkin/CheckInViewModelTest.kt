package de.culture4life.luca.ui.checkin

import androidx.navigation.NavController
import androidx.navigation.NavDestination
import de.culture4life.luca.LucaUnitTest
import de.culture4life.luca.R
import de.culture4life.luca.checkin.CheckInManager
import de.culture4life.luca.crypto.CryptoManager
import de.culture4life.luca.crypto.CryptoManagerTest
import de.culture4life.luca.crypto.DailyKeyUnavailableException
import de.culture4life.luca.crypto.DailyPublicKeyData
import de.culture4life.luca.document.Document
import de.culture4life.luca.document.DocumentManager
import de.culture4life.luca.meeting.MeetingManager
import de.culture4life.luca.network.pojo.LocationResponseData
import de.culture4life.luca.registration.RegistrationManager
import de.culture4life.luca.testtools.samples.SampleDocuments
import de.culture4life.luca.ui.ViewEvent
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import java.security.interfaces.ECPublicKey
import java.util.*

class CheckInViewModelTest : LucaUnitTest() {

    private lateinit var viewModel: CheckInViewModel
    private lateinit var registrationManager: RegistrationManager
    private lateinit var meetingManager: MeetingManager
    private lateinit var checkInManager: CheckInManager
    private lateinit var cryptoManager: CryptoManager
    private lateinit var documentManager: DocumentManager

    @Before
    fun before() {
        val applicationSpy = spy(application)
        registrationManager = spy(application.registrationManager)
        meetingManager = spy(application.meetingManager)
        checkInManager = spy(application.checkInManager)
        cryptoManager = spy(application.cryptoManager)
        documentManager = spy(application.documentManager)
        doReturn(registrationManager).`when`(applicationSpy).registrationManager
        doReturn(meetingManager).`when`(applicationSpy).meetingManager
        doReturn(checkInManager).`when`(applicationSpy).checkInManager
        doReturn(cryptoManager).`when`(applicationSpy).cryptoManager
        doReturn(documentManager).`when`(applicationSpy).documentManager

        doReturn(Maybe.just(UUID.randomUUID())).`when`(registrationManager).getUserIdIfAvailable()
        application.preferencesManager.initialize(application).blockingAwait()
        viewModel = spy(CheckInViewModel(applicationSpy))
    }

    @Test
    fun `If no daily public key is available then isDailyPublicKeyAvailable returns false`() {
        // Given
        doReturn(Single.error<DailyPublicKeyData>(DailyKeyUnavailableException(Throwable()))).`when`(cryptoManager).getDailyPublicKey()

        // When
        viewModel.initialize().test()
        rxSchedulersRule.testScheduler.triggerActions()

        // Then
        assertFalse(viewModel.isDailyPublicKeyAvailable.value!!)
    }

    @Test
    fun `If a daily public key is available then isDailyPublicKeyAvailable returns true`() {
        // Given
        doReturn(Single.just(CryptoManagerTest.DAILY_KEY_NOT_EXPIRED)).`when`(cryptoManager).getDailyPublicKey()

        // When
        viewModel.initialize().test()
        rxSchedulersRule.testScheduler.triggerActions()

        // Then
        assertTrue(viewModel.isDailyPublicKeyAvailable.value!!)
    }

    @Test
    fun getScannerIdFromUrl_validCheckInUrl_emitsUuid() {
        CheckInViewModel.getScannerIdFromUrl(CHECK_IN_URL)
            .map(UUID::toString)
            .test()
            .assertValue(SCANNER_ID)
    }

    @Test
    fun getScannerIdFromUrl_validCheckInUrlWithLucaAndCwaData_emitsUuid() {
        CheckInViewModel.getScannerIdFromUrl(CHECK_IN_URL_WITH_LUCA_AND_CWA_DATA)
            .map(UUID::toString)
            .test()
            .assertValue(SCANNER_ID)
    }

    @Test
    fun getEncodedAdditionalDataFromUrlIfAvailable_validCheckInUrl_completesEmpty() {
        CheckInViewModel.getEncodedAdditionalDataFromUrlIfAvailable(CHECK_IN_URL)
            .test()
            .assertNoValues()
            .assertComplete()
    }

    @Test
    fun getEncodedAdditionalDataFromUrlIfAvailable_validCheckInUrlWithLucaData_emitsLucaData() {
        CheckInViewModel.getEncodedAdditionalDataFromUrlIfAvailable(CHECK_IN_URL_WITH_LUCA_DATA)
            .test()
            .assertValue(LUCA_DATA)
    }

    @Test
    fun getEncodedAdditionalDataFromUrlIfAvailable_validCheckInUrlWithLucaAndCwaData_emitsLucaData() {
        CheckInViewModel.getEncodedAdditionalDataFromUrlIfAvailable(CHECK_IN_URL_WITH_LUCA_AND_CWA_DATA)
            .test()
            .assertValue(LUCA_DATA)
    }

    @Test
    fun getEncodedAdditionalDataFromUrlIfAvailable_preparedUrl_doesNotCrash() {
        CheckInViewModel.getEncodedAdditionalDataFromUrlIfAvailable("$WEB_APP_URL/CWA1/#")
            .test()
            .await()
            .assertNoErrors()
    }

    @Test
    fun `If no contact data is provided then isContactDataMissing returns true`() {
        // Given
        doReturn(Single.just(false)).`when`(registrationManager).hasProvidedRequiredContactData()

        // When
        viewModel.checkIfContactDataMissing()
        rxSchedulersRule.testScheduler.triggerActions()

        // Then
        assertTrue(viewModel.isContactDataMissing.value!!)
    }

    @Test
    fun `If currently hosting a meeting then navigate to meeting fragment when checking`() {
        // Given
        val navController = mock<NavController>()
        val currentDestination = mock<NavDestination>()
        whenever(currentDestination.id).thenReturn(R.id.checkInFragment)
        whenever(navController.currentDestination).thenReturn(currentDestination)
        viewModel.setNavigationController(navController)
        doReturn(Single.just(true)).`when`(meetingManager).isCurrentlyHostingMeeting

        // When
        viewModel.checkIfHostingMeeting()
        rxSchedulersRule.testScheduler.triggerActions()

        // Then
        verify(navController, times(1)).navigate(eq(R.id.action_checkInFragment_to_meetingFragment), eq(null))
    }

    @Test
    fun `Can process bar code if given url has correct host, path and id`() {
        assertTrue(viewModel.canProcessBarcode("$WEB_APP_URL/meeting/$SCANNER_ID"))
    }

    @Test
    fun `Checking in with a valid deeplink completes without errors`() {
        // Given
        doReturn(Completable.error(IllegalStateException())).`when`(checkInManager).assertCheckedInToPrivateMeeting()
        var addedAdditionalCheckInProperties = 0
        doReturn(Completable.fromAction { addedAdditionalCheckInProperties++ }).`when`(checkInManager).addAdditionalCheckInProperties(any(), any())
        doReturn(Single.just(mock<ECPublicKey>())).`when`(checkInManager).getLocationPublicKey(UUID.fromString(SCANNER_ID))
        val qrCodeData = mock<QrCodeData>()
        var numberOfTimesCheckedIn = 0
        doReturn(Completable.fromAction { numberOfTimesCheckedIn++ }).`when`(checkInManager).checkIn(UUID.fromString(SCANNER_ID), qrCodeData)
        doReturn(Single.just(qrCodeData)).`when`(viewModel).generateQrCodeData(false, false)

        // When
        val observer = viewModel.handleSelfCheckInDeepLink(CHECK_IN_URL_WITH_LUCA_DATA).test()
        rxSchedulersRule.testScheduler.triggerActions()

        // Then
        observer.await().assertNoErrors()
        assertEquals(numberOfTimesCheckedIn, 1)
        assertEquals(addedAdditionalCheckInProperties, 1)
    }

    @Test
    fun `Anonymous Checking in with a valid deeplink completes without errors`() {
        // Given
        doReturn(Completable.error(IllegalStateException())).`when`(checkInManager).assertCheckedInToPrivateMeeting()
        var addedAdditionalCheckInProperties = 0
        doReturn(Completable.fromAction { addedAdditionalCheckInProperties++ }).`when`(checkInManager).addAdditionalCheckInProperties(any(), any())
        doReturn(Single.just(mock<ECPublicKey>())).`when`(checkInManager).getLocationPublicKey(UUID.fromString(SCANNER_ID))
        val qrCodeData = mock<QrCodeData>()
        var numberOfTimesCheckedIn = 0
        doReturn(Completable.fromAction { numberOfTimesCheckedIn++ }).`when`(checkInManager).checkIn(UUID.fromString(SCANNER_ID), qrCodeData)
        doReturn(Single.just(qrCodeData)).`when`(viewModel).generateQrCodeData(true, false)

        // When
        val observer = viewModel.handleSelfCheckInDeepLink(CHECK_IN_URL_WITH_LUCA_DATA, true, false).test()
        rxSchedulersRule.testScheduler.triggerActions()

        // Then
        observer.await().assertNoErrors()
        assertEquals(numberOfTimesCheckedIn, 1)
        assertEquals(addedAdditionalCheckInProperties, 1)
    }

    @Test
    fun `Calling onCheckInMultiConfirmDismissed emits correct event`() {
        // When
        viewModel.onCheckInMultiConfirmDismissed()

        // Then
        assertEquals(ViewEvent(true), viewModel.showCameraPreview.value)
    }

    @Test
    fun `Calling onImportDocumentConfirmationDismissed emits correct event`() {
        // When
        viewModel.onImportDocumentConfirmationDismissed()

        // Then
        assertEquals(ViewEvent(true), viewModel.showCameraPreview.value)
    }

    @Test
    fun `Joining private meeting with valid url completes without errors with anonymous qr code data`() {
        // Given
        doReturn(Completable.complete()).`when`(checkInManager).assertCheckedInToPrivateMeeting()
        var addedAdditionalCheckInProperties = 0
        doReturn(Completable.fromAction { addedAdditionalCheckInProperties++ }).`when`(checkInManager).addAdditionalCheckInProperties(any(), any())
        doReturn(Single.just(mock<ECPublicKey>())).`when`(checkInManager).getLocationPublicKey(UUID.fromString(SCANNER_ID))
        val qrCodeData = mock<QrCodeData>()
        var numberOfTimesCheckedIn = 0
        doReturn(Completable.fromAction { numberOfTimesCheckedIn++ }).`when`(checkInManager).checkIn(UUID.fromString(SCANNER_ID), qrCodeData)
        doReturn(Single.just(qrCodeData)).`when`(viewModel).generateQrCodeData(true, false)

        // When
        viewModel.onPrivateMeetingJoinApproved(CHECK_IN_URL_WITH_LUCA_DATA)
        rxSchedulersRule.testScheduler.triggerActions()

        // Then
        assertTrue(viewModel.errors.value.isNullOrEmpty())
        assertEquals(numberOfTimesCheckedIn, 1)
        assertEquals(addedAdditionalCheckInProperties, 1)
    }

    @Test
    fun `Calling onPrivateMeetingJoinDismissed emits correct event`() {
        // When
        viewModel.onPrivateMeetingJoinDismissed("123")

        // Then
        assertEquals(ViewEvent(true), viewModel.showCameraPreview.value)
    }

    @Test
    fun `When requesting to create a private meeting navigate to correct destination`() {
        // Given
        val navController = mock<NavController>()
        val currentDestination = mock<NavDestination>()
        whenever(currentDestination.id).thenReturn(R.id.checkInFragment)
        whenever(navController.currentDestination).thenReturn(currentDestination)
        viewModel.setNavigationController(navController)
        doReturn(Completable.complete()).`when`(meetingManager).createPrivateMeeting()

        // When
        viewModel.onPrivateMeetingCreationRequested()
        rxSchedulersRule.testScheduler.triggerActions()

        // Then
        verify(navController, times(1)).navigate(eq(R.id.action_checkInFragment_to_meetingFragment), eq(null))
    }

    @Test
    fun `Calling onPrivateMeetingCreationDismissed emits correct event`() {
        // When
        viewModel.onPrivateMeetingCreationDismissed()

        // Then
        assertEquals(ViewEvent(true), viewModel.showCameraPreview.value)
    }

    @Test
    fun `Calling onContactDataMissingDialogDismissed emits correct event`() {
        // When
        viewModel.onContactDataMissingDialogDismissed()

        // Then
        assertEquals(ViewEvent(true), viewModel.showCameraPreview.value)
    }

    @Test
    fun `Calling processBarcode with location url emits correct event`() {
        // Given
        val locationData = mock<LocationResponseData>()
        doReturn(Single.just(locationData)).`when`(checkInManager).getLocationDataFromScannerId(SCANNER_ID)

        // When
        val observer = viewModel.processBarcode(CHECK_IN_URL_WITH_LUCA_DATA).test()
        rxSchedulersRule.testScheduler.triggerActions()

        // Then
        observer.await().assertNoErrors()
        assertEquals(ViewEvent(androidx.core.util.Pair(CHECK_IN_URL_WITH_LUCA_DATA, locationData)), viewModel.checkInMultiConfirm.value)
    }

    @Test
    fun `Calling processBarcode with document content emits correct event`() {
        // Given
        val document = SampleDocuments.ErikaMustermann.EudccFullyVaccinated()
        val documentQrCodeContent = document.qrCodeContent
        doReturn(Single.just(mock<Document>())).`when`(documentManager).parseAndValidateEncodedDocument(documentQrCodeContent)

        // When
        val observer = viewModel.processBarcode(documentQrCodeContent).test()
        rxSchedulersRule.testScheduler.triggerActions()

        // Then
        observer.await().assertNoErrors()
        assertEquals(ViewEvent(documentQrCodeContent), viewModel.possibleDocumentData.value)
    }

    companion object {
        private const val WEB_APP_URL = "https://app.luca-app.de/webapp"
        private const val SCANNER_ID = "81d9e1db-7050-4557-b1ca-a9e4fe899bd9"
        private const val LUCA_DATA = "e30"
        private const val CHECK_IN_URL = "$WEB_APP_URL/$SCANNER_ID"
        private const val CHECK_IN_URL_WITH_LUCA_DATA = "$CHECK_IN_URL#$LUCA_DATA"
        private const val CWA_URL_SUFFIX =
            "/CWA1/CAESLAgBEhNEb2xvcmVzIGN1bHBhIHV0IHNpGhNOb3N0cnVkIE5hbSBpZCBlbGlnGnYIARJggwLMzE153tQwAOf2MZoUXXfzWTdlSpfS99iZffmcmxOG9njSK4RTimFOFwDh6t0Tyw8XR01ugDYjtuKwjjuK49Oh83FWct6XpefPi9Skjxvvz53i9gaMmUEc96pbtoaAGhDL1rYQOi3Bh_YYps7XagWYIgcIARAIGIQF"
        private const val CHECK_IN_URL_WITH_LUCA_AND_CWA_DATA = CHECK_IN_URL_WITH_LUCA_DATA + CWA_URL_SUFFIX
    }
}
