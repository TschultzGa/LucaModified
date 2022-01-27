package de.culture4life.luca.registration

import androidx.test.runner.AndroidJUnit4
import com.google.gson.JsonObject
import de.culture4life.luca.LucaUnitTest
import de.culture4life.luca.crypto.encodeToBase64
import de.culture4life.luca.network.NetworkManager
import de.culture4life.luca.network.endpoints.LucaEndpointsV3
import de.culture4life.luca.network.pojo.UserRegistrationRequestData
import de.culture4life.luca.preference.PreferencesManager
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import okhttp3.ResponseBody.Companion.toResponseBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response
import java.util.*

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class RegistrationManagerUnitTest : LucaUnitTest() {

    private val dummyContactData = "dummy contact".toByteArray()
    private val dummyIv = "dummy iv".toByteArray()
    private val dummyMac = "dummy mac".toByteArray()
    private val dummySignature = "dummy signature".toByteArray()
    private val dummyGuestKeyPairPublicKey = "dummy pair public key".toByteArray()
    private val sampleUserUUID = UUID.randomUUID()
    private val sampleUserDataForUpdate = buildSampleUserDate()
    private val sampleUserDataForRegister = buildSampleUserDate().apply {
        guestKeyPairPublicKey = dummyGuestKeyPairPublicKey.encodeToBase64()
    }
    private val sampleRegisterUserResponse = JsonObject().also { it.addProperty("userId", sampleUserUUID.toString()) }
    private val mockLucaEndpoints = mock<LucaEndpointsV3>().also {
        whenever(it.updateUser(any(), any())).then { Completable.complete() }
        whenever(it.registerUser(any())).then { Single.just(sampleRegisterUserResponse) }
    }
    private val preferencesManager = PreferencesManager()
    private val registrationManager = spy(buildRegistrationManager()) {
        whenever(it.createUserRegistrationRequestData()).then { Single.just(sampleUserDataForRegister) }
    }
    private val testStartTimestamp = System.currentTimeMillis()

    @Before
    fun setup() {
        getInitializedManager(preferencesManager)
    }

    @Test
    fun reportActiveUser_withUserData_doesUpdate() {
        preferencesManager.persist(RegistrationManager.USER_ID_KEY, sampleUserUUID).blockingAwait()
        whenOnReportActiveUser()
        verify(mockLucaEndpoints).updateUser(eq(sampleUserUUID.toString()), refEq(sampleUserDataForUpdate))
        assertActivityTimestampUpdated()
    }

    @Test
    fun reportActiveUser_withoutUserData_doesNothing() {
        preferencesManager.delete(RegistrationManager.USER_ID_KEY).blockingAwait()
        whenOnReportActiveUser()
        verifyNoInteractions(mockLucaEndpoints)
        assertActivityReportTimestampNotStoredYet()
    }

    @Test
    fun reportActiveUser_recentlyDone_doesNothing() {
        preferencesManager.persist(RegistrationManager.USER_ID_KEY, sampleUserUUID).blockingAwait()
        preferencesManager.persist(RegistrationManager.LAST_USER_ACTIVITY_REPORT_TIMESTAMP_KEY, testStartTimestamp).blockingAwait()
        whenOnReportActiveUser()
        verifyNoInteractions(mockLucaEndpoints)
        assertActivityTimestampNotUpdated()
    }

    @Test
    fun reportActiveUser_longAgoDone_doesUpdate() {
        val shouldTriggerNextActivityReport = testStartTimestamp - RegistrationManager.USER_ACTIVITY_REPORT_INTERVAL
        preferencesManager.persist(RegistrationManager.USER_ID_KEY, sampleUserUUID).blockingAwait()
        preferencesManager.persist(RegistrationManager.LAST_USER_ACTIVITY_REPORT_TIMESTAMP_KEY, shouldTriggerNextActivityReport).blockingAwait()
        whenOnReportActiveUser()
        verify(mockLucaEndpoints).updateUser(eq(sampleUserUUID.toString()), refEq(sampleUserDataForUpdate))
        verifyNoMoreInteractions(mockLucaEndpoints)
        assertActivityTimestampUpdated()
    }

    @Test
    fun reportActiveUser_updateUser403Response_registerUser() {
        preferencesManager.persist(RegistrationManager.USER_ID_KEY, sampleUserUUID).blockingAwait()
        updateUserWillRespondWithError(403)
        whenOnReportActiveUser()
        verify(mockLucaEndpoints).updateUser(eq(sampleUserUUID.toString()), refEq(sampleUserDataForUpdate))
        verify(mockLucaEndpoints).registerUser(refEq(sampleUserDataForRegister))
        verifyNoMoreInteractions(mockLucaEndpoints)
        assertActivityTimestampUpdated()
    }


    @Test
    fun reportActiveUser_updateUser404Response_registerUser() {
        preferencesManager.persist(RegistrationManager.USER_ID_KEY, sampleUserUUID).blockingAwait()
        updateUserWillRespondWithError(404)
        whenOnReportActiveUser()
        verify(mockLucaEndpoints).updateUser(eq(sampleUserUUID.toString()), refEq(sampleUserDataForUpdate))
        verify(mockLucaEndpoints).registerUser(refEq(sampleUserDataForRegister))
        verifyNoMoreInteractions(mockLucaEndpoints)
        assertActivityTimestampUpdated()
    }

    @Test
    fun reportActiveUser_updateUserUnhandledHttpErrorResponse_justThrowFurther() {
        preferencesManager.persist(RegistrationManager.USER_ID_KEY, sampleUserUUID).blockingAwait()
        updateUserWillRespondWithError(499)
        assertThrows(HttpException::class.java) { whenOnReportActiveUser() }
        verify(mockLucaEndpoints).updateUser(eq(sampleUserUUID.toString()), refEq(sampleUserDataForUpdate))
        verifyNoMoreInteractions(mockLucaEndpoints)
        assertActivityReportTimestampNotStoredYet()
    }

    @Test
    fun registerUser_onSuccess_storesActivityReportTimestamp() {
        assertActivityReportTimestampNotStoredYet()
        registrationManager.registerUser().blockingAwait()
        assertActivityTimestampUpdated()
    }

    @Test
    fun registerUser_onFailure_dontStoreActivityReportTimestamp() {
        registerUserWillRespondWithError(499)
        assertThrows(HttpException::class.java) { registrationManager.registerUser().blockingAwait() }
        assertActivityReportTimestampNotStoredYet()
    }

    private fun whenOnReportActiveUser() {
        registrationManager.reportActiveUser().blockingAwait()
    }

    private fun buildSampleUserDate() = UserRegistrationRequestData().apply {
        encryptedContactData = dummyIv.encodeToBase64()
        iv = dummyContactData.encodeToBase64()
        mac = dummyMac.encodeToBase64()
        signature = dummySignature.encodeToBase64()
    }

    private fun buildRegistrationManager(): RegistrationManager {
        return RegistrationManager(
            preferencesManager,
            buildMockNetworkManager(),
            mock()
        )
    }

    private fun updateUserWillRespondWithError(httpCode: Int) {
        whenever(mockLucaEndpoints.updateUser(any(), any())).thenThrow(
            HttpException(
                Response.error<String>(
                    httpCode,
                    "dummy reason".toResponseBody()
                )
            )
        )
    }

    private fun registerUserWillRespondWithError(httpCode: Int) {
        whenever(mockLucaEndpoints.registerUser(any())).thenThrow(
            HttpException(
                Response.error<String>(
                    httpCode,
                    "dummy reason".toResponseBody()
                )
            )
        )
    }

    private fun buildMockNetworkManager() = mock<NetworkManager>().also {
        whenever(it.initialize(any())).then { Completable.complete() }
        whenever(it.lucaEndpointsV3).then { Single.just(mockLucaEndpoints) }
    }

    private fun storedActivityReportTimestamp() =
        preferencesManager.restoreOrDefault(RegistrationManager.LAST_USER_ACTIVITY_REPORT_TIMESTAMP_KEY, 0L).blockingGet()


    private fun assertActivityReportTimestampNotStoredYet() {
        assertThat(storedActivityReportTimestamp()).isEqualTo(0L)
    }

    private fun assertActivityTimestampUpdated() {
        assertThat(storedActivityReportTimestamp()).isGreaterThan(testStartTimestamp)
    }

    private fun assertActivityTimestampNotUpdated() {
        assertThat(storedActivityReportTimestamp()).isEqualTo(testStartTimestamp)
    }
}
