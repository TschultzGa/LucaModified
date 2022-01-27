package de.culture4life.luca.connect

import androidx.test.runner.AndroidJUnit4
import de.culture4life.luca.LucaUnitTest
import de.culture4life.luca.crypto.decodeFromHex
import de.culture4life.luca.crypto.encodeToHex
import de.culture4life.luca.registration.Person
import io.reactivex.rxjava3.core.Observable
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config

@Config(sdk = [28])
@RunWith(AndroidJUnit4::class)
class ConnectManagerTest : LucaUnitTest() {

    private lateinit var connectManager: ConnectManager

    @Before
    fun setup() {
        connectManager = spy(getInitializedManager(application.connectManager))
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