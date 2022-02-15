package de.culture4life.luca.health

import android.content.Context
import de.culture4life.luca.LucaApplication
import de.culture4life.luca.Manager
import de.culture4life.luca.consent.ConsentManager
import de.culture4life.luca.consent.ConsentManager.Companion.ID_POSTAL_CODE_MATCHING
import de.culture4life.luca.crypto.CryptoManager
import de.culture4life.luca.network.NetworkManager
import de.culture4life.luca.network.pojo.HealthDepartment
import de.culture4life.luca.preference.PreferencesManager
import de.culture4life.luca.registration.RegistrationManager
import de.culture4life.luca.util.ThrowableUtil
import de.culture4life.luca.util.TimeUtil
import de.culture4life.luca.util.retryWhenWithDelay
import de.culture4life.luca.util.verifyJwt
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import timber.log.Timber
import java.util.concurrent.TimeUnit

class HealthDepartmentManager(
    private val preferencesManager: PreferencesManager,
    private val networkManager: NetworkManager,
    private val consentManager: ConsentManager,
    private val registrationManager: RegistrationManager,
    private val cryptoManager: CryptoManager
) : Manager() {

    private var cachedResponsibleHealthDepartment: Maybe<ResponsibleHealthDepartment>? = null

    /**
     * Should emit a Boolean each time the responsible health department changed.
     * `true` if a department is available, `false` otherwise.
     */
    private val responsibleHealthDepartmentUpdateSubject = BehaviorSubject.create<Boolean>()

    override fun doInitialize(context: Context): Completable {
        return Completable.mergeArray(
            preferencesManager.initialize(context),
            networkManager.initialize(context),
            consentManager.initialize(context),
            registrationManager.initialize(context)
        ).andThen(Completable.defer {
            if (LucaApplication.isRunningUnitTests()) {
                Completable.complete()
            } else {
                Completable.mergeArray(
                    invokeResponsibleHealthDepartmentUpdateIfRequired(),
                    invoke(startObservingPostalCodeUsagePermission())
                )
            }
        })
    }

    private fun startObservingPostalCodeUsagePermission(): Completable {
        return consentManager.getConsentAndChanges(ID_POSTAL_CODE_MATCHING)
            .skip(1) // we only care about changes
            .flatMapCompletable {
                Completable.defer {
                    if (it.approved) updateResponsibleHealthDepartmentIfRequired() else deleteResponsibleHealthDepartment()
                }.onErrorComplete()
            }
    }

    fun invokeResponsibleHealthDepartmentUpdateIfRequired(): Completable {
        return invokeDelayed(updateResponsibleHealthDepartmentIfRequired()
            .doOnError { Timber.w("Unable to update responsible health department: $it") }
            .retryWhenWithDelay(10, 30, Schedulers.io()) {
                ThrowableUtil.isNetworkError(it)
            }, TimeUnit.SECONDS.toMillis(1)
        )
    }

    fun updateResponsibleHealthDepartmentIfRequired(): Completable {
        return registrationManager.hasCompletedRegistration()
            .filter { it }
            .flatMapSingle { consentManager.getConsent(ID_POSTAL_CODE_MATCHING) }
            .filter { it.approved }
            .flatMapSingle {
                getResponsibleHealthDepartmentIfAvailable()
                    .map { TimeUtil.getCurrentMillis() - it.updateTimestamp > MINIMUM_UPDATE_DELAY }
                    .defaultIfEmpty(true)
            }
            .filter { it }
            .flatMapCompletable { updateResponsibleHealthDepartment() }
    }

    fun updateResponsibleHealthDepartment(): Completable {
        return getPostalCodeCode()
            .flatMapMaybe { postalCode ->
                fetchHealthDepartment(postalCode)
                    .flatMapSingle { createResponsibleHealthDepartment(it, postalCode) }
            }
            .filter { isNewResponsibleHealthDepartment(it).blockingGet() }
            .doOnSuccess { Timber.i("New responsible health department: $it") }
            .switchIfEmpty(
                deleteResponsibleHealthDepartment()
                    .andThen(Maybe.empty<ResponsibleHealthDepartment>())
                    .doOnSubscribe { Timber.i("No responsible health department available") }
            )
            .flatMapCompletable(::persistResponsibleHealthDepartment)
    }

    private fun isNewResponsibleHealthDepartment(newResponsibleHealthDepartment: ResponsibleHealthDepartment): Single<Boolean> {
        return getResponsibleHealthDepartmentIfAvailable()
            .map { it != newResponsibleHealthDepartment }
            .defaultIfEmpty(true)
    }

    fun getResponsibleHealthDepartmentUpdates(): Observable<Boolean> {
        return responsibleHealthDepartmentUpdateSubject
    }

    fun getResponsibleHealthDepartmentIfAvailable(): Maybe<ResponsibleHealthDepartment> {
        return Maybe.defer {
            if (cachedResponsibleHealthDepartment == null) {
                cachedResponsibleHealthDepartment = restoreResponsibleHealthDepartmentIfAvailable().cache()
            }
            cachedResponsibleHealthDepartment!!
        }
    }

    fun restoreResponsibleHealthDepartmentIfAvailable(): Maybe<ResponsibleHealthDepartment> {
        return preferencesManager.restoreIfAvailable(RESPONSIBLE_HEALTH_DEPARTMENT_KEY, ResponsibleHealthDepartment::class.java)
            .doOnSuccess { cachedResponsibleHealthDepartment = Maybe.just(it) }
    }

    fun persistResponsibleHealthDepartment(healthDepartment: ResponsibleHealthDepartment): Completable {
        return preferencesManager.persist(RESPONSIBLE_HEALTH_DEPARTMENT_KEY, healthDepartment)
            .doOnComplete {
                cachedResponsibleHealthDepartment = Maybe.just(healthDepartment)
                responsibleHealthDepartmentUpdateSubject.onNext(true)
            }
    }

    fun deleteResponsibleHealthDepartment(): Completable {
        return preferencesManager.delete(RESPONSIBLE_HEALTH_DEPARTMENT_KEY)
            .doOnComplete {
                cachedResponsibleHealthDepartment = Maybe.empty()
                responsibleHealthDepartmentUpdateSubject.onNext(false)
            }
    }

    fun fetchHealthDepartment(zipCode: String): Maybe<HealthDepartment> {
        return networkManager.lucaEndpointsV4
            .flatMap { it.responsibleHealthDepartment }
            .flatMapObservable { Observable.fromIterable(it) }
            .filter { zipCode in it.zipCodes }
            .toList()
            .flatMapMaybe { departments ->
                Maybe.fromCallable { departments.firstOrNull() }
            }
    }

    fun createResponsibleHealthDepartment(healthDepartment: HealthDepartment, postalCode: String): Single<ResponsibleHealthDepartment> {
        return cryptoManager
            .initialize(context)
            .andThen(cryptoManager.getAndVerifyIssuerCertificate(healthDepartment.id))
            .map { keyIssuerResponseData ->
                require(healthDepartment.name == keyIssuerResponseData.signingKeyData.name)
                healthDepartment.encryptionKeyJwt.verifyJwt(keyIssuerResponseData.certificate.publicKey)
                healthDepartment.signingKeyJwt.verifyJwt(keyIssuerResponseData.certificate.publicKey)
                ResponsibleHealthDepartment(healthDepartment, postalCode)
            }
            .doOnError { Timber.w("Health department jwt verification failed: %s", it.toString()) }
    }

    fun getPostalCodeCode(): Single<String> {
        return consentManager.requestConsentIfRequiredAndAssertApproved(ID_POSTAL_CODE_MATCHING)
            .andThen(registrationManager.getRegistrationData()
                .map { it.postalCode!! })
    }

    companion object {
        private const val RESPONSIBLE_HEALTH_DEPARTMENT_KEY = "responsible_health_department"
        private val MINIMUM_UPDATE_DELAY = TimeUnit.DAYS.toMillis(1)
    }
}