package de.culture4life.luca.connect

import de.culture4life.luca.LucaUnitTest
import de.culture4life.luca.connect.ConnectManager.Companion.KEY_ARCHIVED_CONTACT_DATA
import de.culture4life.luca.connect.ConnectManager.Companion.ROUNDED_TIMESTAMP_ACCURACY
import de.culture4life.luca.connect.ConnectManager.Companion.UPDATE_INTERVAL
import de.culture4life.luca.document.Document
import de.culture4life.luca.document.DocumentManager
import de.culture4life.luca.health.HealthDepartmentManager
import de.culture4life.luca.health.ResponsibleHealthDepartment
import de.culture4life.luca.network.NetworkManager
import de.culture4life.luca.network.endpoints.LucaEndpointsV4
import de.culture4life.luca.notification.LucaNotificationManager
import de.culture4life.luca.preference.PreferencesManager
import de.culture4life.luca.registration.Person
import de.culture4life.luca.util.TimeUtil
import de.culture4life.luca.util.decodeFromHex
import de.culture4life.luca.util.encodeToBase64
import de.culture4life.luca.util.encodeToHex
import de.culture4life.luca.whatisnew.WhatIsNewManager
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertTrue
import org.joda.time.DateTime
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.doReturn
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import java.security.interfaces.ECPublicKey
import java.time.*
import java.util.*
import kotlin.math.floor
import kotlin.random.Random

class ConnectManagerTest : LucaUnitTest() {

    private lateinit var connectManager: ConnectManager

    @Mock
    private lateinit var documentManager: DocumentManager

    @Mock
    private lateinit var networkManager: NetworkManager

    @Mock
    private lateinit var lucaEndpointsV4: LucaEndpointsV4

    private lateinit var notificationManager: LucaNotificationManager
    private lateinit var healthDepartmentManager: HealthDepartmentManager
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var whatIsNewManager: WhatIsNewManager

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        initializeMockedManagers()
        setupNetworkManager()
        notificationManager = spy(application.notificationManager)
        preferencesManager = spy(application.preferencesManager)
        healthDepartmentManager = spy(application.healthDepartmentManager)
        whatIsNewManager = spy(application.whatIsNewManager)
        connectManager = spy(
            getInitializedManager(
                ConnectManager(
                    preferencesManager,
                    notificationManager,
                    networkManager,
                    application.powManager,
                    application.cryptoManager,
                    application.registrationManager,
                    documentManager,
                    healthDepartmentManager,
                    whatIsNewManager
                )
            )
        )
    }

    @After
    fun after() {
        TimeUtil.clock = Clock.systemUTC()
    }

    private fun initializeMockedManagers() {
        whenever(documentManager.initialize(any())).thenReturn(Completable.complete())
        whenever(networkManager.initialize(any())).thenReturn(Completable.complete())
    }

    private fun setupNetworkManager() {
        whenever(networkManager.getLucaEndpointsV4()).thenReturn(Single.just(lucaEndpointsV4))
    }

    @Test
    fun generateMessageId_validData_expectedResult() {
        connectManager.generateMessageId(
            healthDepartmentId = "e0fe6752-809a-4c13-8350-6ecf5a70e1ce",
            notificationId = "1eAyKyWyFA1kDGxreciT7Q==",
            roundedTimestamp = 1639046100000
        ).test().assertValue("FRq2lIdyA5l7+fgkZuBagg==")
    }

    @Test
    fun generateSimplifiedNameHash() {
        val person = Person("Prof. Dr. Tom Jerry", "Süßmeier")
        connectManager.generateSimplifiedNameHash(person)
            .map { it.encodeToHex() }
            .test()
            .assertValue("edec73a654be87429f02b53f39af85f3297720abc02ef7cfc5c0bf2514483212")
    }

    @Test
    fun generatePhoneNumberHash() {
        val phoneNumber = "0171 1234567"
        connectManager.generatePhoneNumberHash(phoneNumber)
            .map { it.encodeToHex() }
            .test()
            .assertValue("26fe61aab2698bb3696dc47387e6c64172c6c716727917caaf6ff965d030cb3d")
    }

    @Test
    fun generateHashPrefix_validHash_expectedPrefix() {
        val hash = "554abf5d201142b52647f4ca3f777286ece7375007c6f65fc9c2acb52f270945".decodeFromHex()
        connectManager.generateHashPrefix(hash)
            .map { it.encodeToHex() }
            .test()
            .assertValue("554aa0")
    }

    @Test
    fun getEnrollmentSupportedButNotRecognizedStatusAndChanges_notSupported_isFalse() {
        givenEnrollmentSupported(false)
        givenEnrollmentSupportRecognized(false)
        givenEnrolled(false)
        connectManager.getEnrollmentSupportedButNotRecognizedStatusAndChanges()
            .test().assertValue(false)
    }

    @Test
    fun getEnrollmentSupportedButNotRecognizedStatusAndChanges_isSupported_isTrue() {
        givenEnrollmentSupported(true)
        givenEnrollmentSupportRecognized(false)
        givenEnrolled(false)
        connectManager.getEnrollmentSupportedButNotRecognizedStatusAndChanges()
            .test().assertValue(true)
    }

    @Test
    fun getEnrollmentSupportedButNotRecognizedStatusAndChanges_isRecognized_isFalse() {
        givenEnrollmentSupported(true)
        givenEnrollmentSupportRecognized(true)
        givenEnrolled(false)
        connectManager.getEnrollmentSupportedButNotRecognizedStatusAndChanges()
            .test().assertValue(false)
    }

    @Test
    fun getEnrollmentSupportedButNotRecognizedStatusAndChanges_isEnrolled_isFalse() {
        givenEnrollmentSupported(true)
        // When enrolled is TRUE then should also recognized be TRUE. But when this FALSE case happens we will do the right thing.
        givenEnrollmentSupportRecognized(false)
        givenEnrolled(true)
        connectManager.getEnrollmentSupportedButNotRecognizedStatusAndChanges()
            .test().assertValue(false)
    }

    @Test
    fun `Getting health department id loads department and maps to id`() {
        // Given
        val department = mock<ResponsibleHealthDepartment> {
            whenever(it.id).thenReturn("id123")
        }
        whenever(healthDepartmentManager.getResponsibleHealthDepartmentIfAvailable()).thenReturn(Maybe.just(department))

        // When
        val getting = connectManager.getHealthDepartmentId().test()

        // Then
        getting.await().assertComplete().assertValue("id123")
    }

    @Test
    fun `Getting health department public key loads department and maps to encryption public key`() {
        // Given
        val publicKey = mock<ECPublicKey>()
        val department = mock<ResponsibleHealthDepartment> {
            whenever(it.encryptionPublicKey).thenReturn(publicKey)
        }
        whenever(healthDepartmentManager.getResponsibleHealthDepartmentIfAvailable()).thenReturn(Maybe.just(department))

        // When
        val getting = connectManager.getHealthDepartmentPublicKey().test()

        // Then
        getting.await().assertComplete().assertValue(publicKey)
    }

    @Test
    fun `Getting contact archive entries returns stored value from preferences`() {
        // Given
        val entry = ConnectContactArchive.Entry("id", 123L)
        val archive = ConnectContactArchive(listOf(entry))
        doReturn(Maybe.just(archive))
            .whenever(preferencesManager)
            .restoreIfAvailable(eq(KEY_ARCHIVED_CONTACT_DATA), eq(ConnectContactArchive::class.java))

        // When
        val getting = connectManager.getContactArchiveEntries().test()

        // Then
        getting.await().assertComplete().assertValue(entry)
    }

    @Test
    fun `Filling real message ids with fake message ids leads to list that contains all real and 0 to max fake ids`() {
        // Given
        val realMessageIds = listOf(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString())

        // When
        val filledMessageIds =
            connectManager.fillMessageIdsWithFakeMessageIds(UUID.randomUUID().toString(), UUID.randomUUID().toString(), realMessageIds).blockingGet()

        // Then
        assertEquals(
            "Correct message ids need to be present in request data besides fake ones",
            realMessageIds,
            filledMessageIds.filter { realMessageIds.contains(it) }
        )
        assertTrue(filledMessageIds.size >= realMessageIds.size)
    }

    @Test
    fun `Generating a notification returns base 64 encoded random id`() {
        // Given
        val randomBase64ByteArray = Random.nextBytes(16).encodeToBase64()

        // When
        val generating = connectManager.generateNewNotificationId().test()

        // Then
        generating.await().assertComplete()
        generating.assertValue { randomBase64ByteArray.length == it.length }
    }

    @Test
    fun `Generating a notification id restores notification id or generates new one if empty`() {
        // Given
        val uuid = UUID.randomUUID().toString()
        whenever(connectManager.restoreNotificationIdIfAvailable()).thenReturn(Maybe.empty())
        whenever(connectManager.generateNewNotificationId()).thenReturn(Single.just(uuid))

        // When
        val getting = connectManager.getNotificationId().test()

        // Then
        getting.await().assertComplete()
        assertEquals(uuid, getting.values().first())
    }

    @Test
    fun `If unread messages are available, unread messages subject emits`() {
        // Given
        val unreadMessage = ConnectMessage("id", "title", "content", 123L, false)
        whenever(connectManager.getMessages()).thenReturn(Observable.just(unreadMessage))

        // When
        val unreadSubject = connectManager.getHasUnreadMessagesStatusAndChanges().take(2).test()
        rxSchedulersRule.testScheduler.triggerActions()
        val updateUnreadMessages = connectManager.updateHasUnreadMessages().test()

        // Then
        updateUnreadMessages.await().assertComplete()
        unreadSubject.await().assertComplete()
        unreadSubject.assertValues(false, true)
    }

    @Test
    fun `Updating messages fetches messages, archives them and shows a notification`() {
        // Given
        val message = ConnectMessage("id", "title", "content", 123L, true)
        val now = DateTime.now().withYear(1993).withMonthOfYear(12).withDayOfMonth(20).withTimeAtStartOfDay().millis
        TimeUtil.clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneId.systemDefault())
        doReturn(Observable.just(message)).`when`(connectManager).fetchNewMessages()
        doReturn(Completable.complete()).`when`(connectManager).addToMessageArchive(any())
        doReturn(Completable.complete()).`when`(connectManager).showNewMessageNotification(any())
        doReturn(Completable.complete()).`when`(connectManager).persistLastUpdateTimestamp(any())
        doReturn(Completable.complete()).`when`(connectManager).updateHasUnreadMessages()

        // When
        val fetching = connectManager.updateMessages().test()

        // Then
        fetching.await().assertNoErrors()

        // TODO: test for subscription
        verify(connectManager, atLeast(1)).addToMessageArchive(message)
        verify(connectManager, atLeast(1)).showNewMessageNotification(message)
        verify(connectManager, atLeast(1)).persistLastUpdateTimestamp(now)
        verify(connectManager, atLeast(1)).updateHasUnreadMessages()
    }

    @Test
    fun `Generating a message id returns a Base64 encoded string of a derivated key from the notification id, the department id and the timestamp`() {
        // Given
        val notificationId = "23b0c2e0-f725-446d-a0bb-763eaf682de3"
        val departmentId = "da1baa42-9881-4964-bcb8-248cafbc79a7"
        val timestamp = LocalDateTime.parse("1993-12-20T00:00").atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
        val expectedMessageId = "JSzRPeHTmgRUaG6pfp09zg=="

        // When
        val generatingMessageId = connectManager.generateMessageId(notificationId, departmentId, timestamp).test()

        // Then
        generatingMessageId.await().assertComplete()
        generatingMessageId.assertValue(expectedMessageId)
    }

    @Test
    fun `Generating rounded timestamps since last update only returns timestamps between now and last update`() {
        // Given
        val now = LocalDateTime.parse("1993-12-20T10:00").atZone(ZoneOffset.UTC)
        val nowMinus15Minutes = now.minusMinutes(15)
        val nowMinus20Minutes = now.minusMinutes(20)
        TimeUtil.clock = Clock.fixed(now.toInstant(), ZoneOffset.UTC)
        val expectedTimestamps = listOf(
            roundTimestamp(nowMinus20Minutes.toInstant().toEpochMilli()),
            roundTimestamp(nowMinus20Minutes.plusMinutes(5).toInstant().toEpochMilli()),
            roundTimestamp(nowMinus20Minutes.plusMinutes(10).toInstant().toEpochMilli())
        )
        doReturn(Maybe.just(nowMinus15Minutes.toInstant().toEpochMilli())).`when`(connectManager).restoreLastUpdateTimestampIfAvailable()

        // When
        val gettingTimestamps = connectManager.generateRoundedTimestampsSinceLastUpdate().test()

        // Then
        gettingTimestamps.await().assertComplete()
        gettingTimestamps.assertValueSequence(expectedTimestamps)
    }

    private fun roundTimestamp(timestamp: Long): Long {
        return floor(timestamp.toDouble() / ROUNDED_TIMESTAMP_ACCURACY).toLong() * ROUNDED_TIMESTAMP_ACCURACY
    }

    @Test
    fun `Generating rounded timestamps emits all possible timestamps between start and end with specified accuracy`() {
        // Given
        // 20.12.1993 10:00
        val startTime = LocalDateTime
            .now()
            .withYear(1993)
            .withMonth(12)
            .withDayOfMonth(20)
            .withHour(10)
            .withMinute(0)
            .atZone(ZoneOffset.UTC)

        // 20.12.1993 10:20
        val endTime = startTime.plusMinutes(20)

        val expectedTimestamps = listOf(
            // 10:00
            756381600000,
            // 10:05
            756381900000,
            // 10:10
            756382200000,
            // 10:15
            756382500000
        )

        // When
        val gettingTimestamps =
            connectManager.generateRoundedTimestamps(startTime.toInstant().toEpochMilli(), endTime.toInstant().toEpochMilli()).test()

        // Then
        gettingTimestamps.await().assertComplete().assertValueSequence(expectedTimestamps)
    }

    @Test
    fun `Getting next recommended update delay returns correct delay when not yet over interval`() {
        // Given
        val smallerThanIntervalDuration = UPDATE_INTERVAL - 100
        doReturn(Single.just(smallerThanIntervalDuration)).`when`(connectManager).getDurationSinceLastUpdate()

        // When
        val getDelay = connectManager.getNextRecommendedUpdateDelay().test()

        // Then
        getDelay.await().assertComplete()
        getDelay.assertValue(100L)
    }

    @Test
    fun `Getting next recommended update delay returns zero when last update was long ago`() {
        // Given
        val biggerThanIntervalDuration = UPDATE_INTERVAL + 1
        whenever(connectManager.getDurationSinceLastUpdate()).thenReturn(Single.just(biggerThanIntervalDuration))

        // When
        val getDelay = connectManager.getNextRecommendedUpdateDelay().test()

        // Then
        getDelay.await().assertComplete().assertValue(0L)
    }

    @Test
    fun `Showing ConnectMessage as notification delegates to notification manager`() {
        // Given
        val message = ConnectMessage("id", "This is a title", "This is my content", 0, false)

        // When
        val showingNotification = connectManager.showNewMessageNotification(message).test()

        // Then
        showingNotification.await().assertComplete()
        val expectedId = LucaNotificationManager.getNotificationId(LucaNotificationManager.NOTIFICATION_ID_CONNECT_MESSAGE, message.id)
        verify(notificationManager, times(1)).showNotification(eq(expectedId), any()) // TODO: test for subscription
    }

    @Test
    fun `Getting the latest covid certificates returns the latest vaccination and recovery certificate`() {
        // Given
        val vaccination = mock<Document> { whenever(it.isValidVaccination).thenReturn(true) }
        val recovery = mock<Document> { whenever(it.isValidRecovery).thenReturn(true) }
        whenever(documentManager.getOrRestoreDocuments()).thenReturn(Observable.just(vaccination, recovery))

        // When
        val latestCertificates = connectManager.getLatestCovidCertificates().test()

        // Then
        latestCertificates.await().assertComplete()
        latestCertificates.assertValues(vaccination, recovery)
    }

    @Test
    fun `Responsible health department update triggers un-enrollment`() {
        // Given
        var unEnrollments = 0
        whenever(connectManager.unEnroll()).thenReturn(Completable.fromAction { unEnrollments++ })

        // When
        healthDepartmentManager.deleteResponsibleHealthDepartment().blockingAwait()
        triggerScheduler()

        // Then
        assertEquals(1, unEnrollments)
    }

    private fun givenEnrolled(isEnrolled: Boolean) {
        whenever(connectManager.getEnrollmentStatusAndChanges()).then { Observable.just(isEnrolled) }
    }

    private fun givenEnrollmentSupported(isSupported: Boolean) {
        whenever(connectManager.getEnrollmentSupportedStatusAndChanges()).then { Observable.just(isSupported) }
    }

    private fun givenEnrollmentSupportRecognized(isRecognized: Boolean) {
        whenever(connectManager.getEnrollmentSupportRecognizedStatusAndChanges()).then { Observable.just(isRecognized) }
    }
}
