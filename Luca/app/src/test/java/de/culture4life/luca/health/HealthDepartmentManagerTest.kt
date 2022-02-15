package de.culture4life.luca.health

import androidx.test.runner.AndroidJUnit4
import de.culture4life.luca.LucaUnitTest
import de.culture4life.luca.consent.Consent
import de.culture4life.luca.consent.ConsentManager
import de.culture4life.luca.consent.ConsentManager.Companion.ID_POSTAL_CODE_MATCHING
import de.culture4life.luca.consent.MissingConsentException
import de.culture4life.luca.network.NetworkManager
import de.culture4life.luca.network.endpoints.LucaEndpointsV4
import de.culture4life.luca.network.pojo.HealthDepartment
import de.culture4life.luca.preference.PreferencesManager
import de.culture4life.luca.registration.RegistrationManager
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import junit.framework.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class HealthDepartmentManagerTest : LucaUnitTest() {

    @Mock
    private lateinit var registrationManager: RegistrationManager

    @Mock
    private lateinit var networkManager: NetworkManager

    @Mock
    private lateinit var lucaEndpointsV4: LucaEndpointsV4

    @Mock
    private lateinit var preferencesManager: PreferencesManager

    @Mock
    private lateinit var consentManager: ConsentManager

    private lateinit var healthDepartmentManager: HealthDepartmentManager

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        givenRegistrationNotCompleted()
        initializeManagers()
        setupPreferencesManager()
        setupNetworkManager()
        setupInitializedHealthDepartmentManager()
    }

    private fun initializeManagers() {
        whenever(preferencesManager.initialize(any())).thenReturn(Completable.complete())
        whenever(networkManager.initialize(any())).thenReturn(Completable.complete())
        whenever(consentManager.initialize(any())).thenReturn(Completable.complete())
        whenever(registrationManager.initialize(any())).thenReturn(Completable.complete())
    }

    private fun setupNetworkManager() {
        whenever(networkManager.lucaEndpointsV4).thenReturn(Single.just(lucaEndpointsV4))
    }

    private fun setupPreferencesManager() {
        whenever(
            preferencesManager.persist(
                eq("responsible_health_department"),
                any<ResponsibleHealthDepartment>()
            )
        ).thenReturn(Completable.complete())
        whenever(preferencesManager.delete("responsible_health_department"))
            .thenReturn(Completable.complete())
        whenever(
            preferencesManager.restoreIfAvailable(
                "responsible_health_department",
                ResponsibleHealthDepartment::class.java
            )
        ).thenReturn(Maybe.just(mock()))
    }

    private fun setupInitializedHealthDepartmentManager() {
        healthDepartmentManager =
            spy(
                getInitializedManager(
                    HealthDepartmentManager(
                        preferencesManager,
                        networkManager,
                        consentManager,
                        registrationManager,
                        application.cryptoManager
                    )
                )
            )
    }

    private fun givenRegistrationNotCompleted() {
        whenever(registrationManager.hasCompletedRegistration()).then { Single.just(false) }
    }

    @Test
    fun `Do not update health department if not required`() {
        // Given
        doReturn(Maybe.empty<ResponsibleHealthDepartment>()).`when`(healthDepartmentManager).getResponsibleHealthDepartmentIfAvailable()
        doReturn(Completable.complete()).`when`(healthDepartmentManager).updateResponsibleHealthDepartment()

        // When
        val updateCheck = healthDepartmentManager.updateResponsibleHealthDepartmentIfRequired().test()

        // Then
        updateCheck.await().assertComplete()
        verify(healthDepartmentManager, times(0)).updateResponsibleHealthDepartment()
    }

    @Test
    fun `Health department update fails if postal code is unusable`() {
        // Given
        val department = mock<ResponsibleHealthDepartment> {
            whenever(it.updateTimestamp).then { 0L }
        }
        doReturn(Maybe.just(department)).`when`(healthDepartmentManager).getResponsibleHealthDepartmentIfAvailable()
        doReturn(Single.error<String>(MissingConsentException(ID_POSTAL_CODE_MATCHING))).`when`(healthDepartmentManager).getPostalCodeCode()

        // When
        val updateCheck = healthDepartmentManager.updateResponsibleHealthDepartment().test()

        // Then
        updateCheck.await().assertError(MissingConsentException::class.java)
    }

    @Test
    fun `Update health department if registration complete and available department is too old`() {
        // Given
        val consent = mock<Consent> { whenever(it.approved).then { true } }
        doReturn(Single.just(consent)).`when`(consentManager).getConsent(ID_POSTAL_CODE_MATCHING)
        val department = mock<ResponsibleHealthDepartment> {
            whenever(it.updateTimestamp).then { 0L }
        }
        doReturn(Completable.complete()).`when`(healthDepartmentManager).updateResponsibleHealthDepartment()
        doReturn(Maybe.just(department)).`when`(healthDepartmentManager).getResponsibleHealthDepartmentIfAvailable()
        whenever(registrationManager.hasCompletedRegistration()).then { Single.just(true) }

        // When
        val updateCheck = healthDepartmentManager.updateResponsibleHealthDepartmentIfRequired().test()

        // Then
        updateCheck.await().assertComplete()
        verify(healthDepartmentManager, times(1)).updateResponsibleHealthDepartment()
    }

    @Test
    fun `When updating health department and a new one is available, the new one can be used`() {
        doReturn(Single.just("12345")).`when`(healthDepartmentManager).getPostalCodeCode()
        doReturn(Maybe.just<HealthDepartment>(mock())).`when`(healthDepartmentManager).fetchHealthDepartment(any())
        doReturn(Single.just<ResponsibleHealthDepartment>(mock())).`when`(healthDepartmentManager).createResponsibleHealthDepartment(any(), any())

        healthDepartmentManager.updateResponsibleHealthDepartment()
            .andThen(healthDepartmentManager.getResponsibleHealthDepartmentIfAvailable())
            .isEmpty
            .test()
            .assertValue(false)
    }

    @Test
    fun `When updating health department and none is available, the old one can not be used`() {
        doReturn(Single.just("12345")).`when`(healthDepartmentManager).getPostalCodeCode()
        doReturn(Maybe.empty<HealthDepartment>()).`when`(healthDepartmentManager).fetchHealthDepartment(any())

        healthDepartmentManager.updateResponsibleHealthDepartment()
            .andThen(healthDepartmentManager.getResponsibleHealthDepartmentIfAvailable())
            .isEmpty
            .test()
            .assertValue(true)
    }

    @Test
    fun `Getting health department if available restores when not cached and returns cached version afterwards`() {
        // Given
        val mockDepartment = mock<ResponsibleHealthDepartment>()
        whenever(
            preferencesManager
                .restoreIfAvailable("responsible_health_department", ResponsibleHealthDepartment::class.java)
        ).thenReturn(Maybe.just(mockDepartment))

        // When
        val gettingFirstTime = healthDepartmentManager.getResponsibleHealthDepartmentIfAvailable().test()
        val gettingSecondTime = healthDepartmentManager.getResponsibleHealthDepartmentIfAvailable().test()

        // Then
        assertEquals(mockDepartment, gettingFirstTime.await().values().first())
        verify(preferencesManager, times(1)).restoreIfAvailable("responsible_health_department", ResponsibleHealthDepartment::class.java)
        assertEquals(mockDepartment, gettingSecondTime.await().values().first())
    }

    @Test
    fun `Deleting health department invokes preferencesManager with correct key`() {
        // When
        val responsibleHealthDepartmentUpdate = healthDepartmentManager.getResponsibleHealthDepartmentUpdates().test()
        val deletion = healthDepartmentManager.deleteResponsibleHealthDepartment().test()

        // Then
        deletion.await().assertComplete()
        assertFalse(responsibleHealthDepartmentUpdate.awaitCount(1).values().first())
        verify(preferencesManager, times(1)).delete("responsible_health_department")
    }

    @Test
    fun `Persisting health department invokes preferencesManager with correct key`() {
        // When
        val responsibleHealthDepartmentUpdate = healthDepartmentManager.getResponsibleHealthDepartmentUpdates().test()
        val persisting = healthDepartmentManager.persistResponsibleHealthDepartment(mock()).test()

        // Then
        persisting.await().assertComplete()
        assertTrue(responsibleHealthDepartmentUpdate.awaitCount(1).values().first())
        verify(preferencesManager, times(1)).persist(eq("responsible_health_department"), any<ResponsibleHealthDepartment>())
    }

    @Test
    fun `Restoring health department invokes preferencesManager with correct key`() {
        // When
        val restoring = healthDepartmentManager.restoreResponsibleHealthDepartmentIfAvailable().test()

        // Then
        restoring.await().assertComplete()
        verify(preferencesManager, times(1)).restoreIfAvailable("responsible_health_department", ResponsibleHealthDepartment::class.java)
    }

}