package de.culture4life.luca.registration

import de.culture4life.luca.BuildConfig
import de.culture4life.luca.LucaInstrumentationTest
import de.culture4life.luca.crypto.CryptoManager
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.BehaviorSubject
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import timber.log.Timber
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class RegistrationManagerTest : LucaInstrumentationTest() {

    private lateinit var registrationManager: RegistrationManager
    private lateinit var cryptoManager: CryptoManager

    @Before
    fun setup() {
        assumeTrue(BuildConfig.DEBUG)
        registrationManager = getInitializedManager(application.registrationManager)
        cryptoManager = getInitializedManager(application.cryptoManager)
    }

    @Test
    fun hasCompletedRegistration_afterRegistration_emitsTrue() {
        registrationManager.registerUser()
            .andThen(registrationManager.hasCompletedRegistration())
            .test().await()
            .assertValue(true)
    }

    @Test
    fun hasCompletedRegistration_afterDeletion_emitsFalse() {
        registrationManager.registerUser()
            .andThen(registrationManager.deleteRegistrationData())
            .andThen(registrationManager.hasCompletedRegistration())
            .test().await()
            .assertValue(false)
    }

    @Test
    @Ignore("For manual invocation only")
    fun reproduceSignatureError() {
        val attempts = BehaviorSubject.create<Int>()
        attempts.onNext(1)

        attempts.doOnNext { Timber.i("Starting signature error reproduction attempt %d", it) }
            .flatMapCompletable { attempt ->
                registerNewUser()
                    .andThen(updateContactData())
                    .andThen(updateContactData())
                    .andThen(updateContactData())
                    .andThen(deleteUser())
                    .doOnComplete { attempts.onNext(attempt + 1) }
            }
            .test()
            .await(1, TimeUnit.HOURS)
    }

    private fun registerNewUser(): Completable {
        return cryptoManager.deleteKeyPair(CryptoManager.ALIAS_GUEST_KEY_PAIR)
            .andThen(Single.fromCallable {
                RegistrationData().apply {
                    firstName = "Erika"
                    lastName = "Mustermann"
                    phoneNumber = "+491711234567"
                    email = "erika.mustermann@example.de"
                    street = "Street"
                    houseNumber = "123"
                    postalCode = "12345"
                    city = "City"
                }
            })
            .flatMapCompletable(registrationManager::persistRegistrationData)
            .andThen(registrationManager.registerUser())
            .doOnSubscribe { Timber.i("Registering user") }
    }

    private fun updateContactData(): Completable {
        return registrationManager.getRegistrationData()
            .map {
                it.also {
                    it.houseNumber = (Math.random() * 1000).roundToInt().toString()
                }
            }
            .flatMapCompletable(registrationManager::persistRegistrationData)
            .andThen(registrationManager.updateUser())
            .doOnSubscribe { Timber.i("Updating user") }
    }

    private fun deleteUser(): Completable {
        return registrationManager.deleteRegistrationOnBackend()
            .andThen(cryptoManager.deleteKeyPair(CryptoManager.ALIAS_GUEST_KEY_PAIR))
            .doOnSubscribe { Timber.i("Deleting user") }
    }

}