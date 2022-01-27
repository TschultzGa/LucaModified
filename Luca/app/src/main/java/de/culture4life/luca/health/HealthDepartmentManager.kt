package de.culture4life.luca.health

import android.content.Context
import de.culture4life.luca.Manager
import de.culture4life.luca.network.NetworkManager
import de.culture4life.luca.network.pojo.HealthDepartment
import de.culture4life.luca.network.pojo.ResponsibleHealthDepartmentRequestData
import de.culture4life.luca.preference.PreferencesManager
import de.culture4life.luca.registration.RegistrationManager
import de.culture4life.luca.util.addTo
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
    private val registrationManager: RegistrationManager
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
            registrationManager.initialize(context)
        ).andThen(invokeResponsibleHealthDepartmentUpdateIfRequired())
    }

    fun invokeResponsibleHealthDepartmentUpdateIfRequired(): Completable {
        return Completable.fromAction {
            updateResponsibleHealthDepartmentIfRequired()
                .doOnError { Timber.w("Unable to update responsible health department: $it") }
                .retryWhen { it.delay(10, TimeUnit.SECONDS) }
                .subscribeOn(Schedulers.io())
                .subscribe()
                .addTo(managerDisposable)
        }
    }

    fun updateResponsibleHealthDepartmentIfRequired(): Completable {
        return postalCodeUsagePermissionGranted()
            .filter { it }
            .flatMapSingle { registrationManager.hasCompletedRegistration() }
            .filter { it }
            .flatMapSingle {
                getResponsibleHealthDepartmentIfAvailable()
                    .map { !it.connectEnrollmentSupported && System.currentTimeMillis() - it.updateTimestamp > MINIMUM_UPDATE_DELAY }
                    .defaultIfEmpty(true)
            }
            .filter { it }
            .flatMapCompletable { updateResponsibleHealthDepartment() }
    }

    fun updateResponsibleHealthDepartment(): Completable {
        return getPostalCodeCode()
            .flatMapMaybe { postalCode ->
                fetchResponsibleHealthDepartment(postalCode)
                    .map { ResponsibleHealthDepartment(it, postalCode) }
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

    fun getResponsibleHealthDepartmentAndChanges(): Observable<ResponsibleHealthDepartment> {
        return getResponsibleHealthDepartmentIfAvailable()
            .toObservable()
            .mergeWith(getResponsibleHealthDepartmentUpdates()
                .flatMapMaybe { getResponsibleHealthDepartmentIfAvailable() })
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

    private fun fetchResponsibleHealthDepartment(zipCode: String): Maybe<HealthDepartment> {
        return networkManager.lucaEndpointsV4
            .flatMap { it.getResponsibleHealthDepartment(ResponsibleHealthDepartmentRequestData(zipCode)) }
            .flatMapMaybe { departments ->
                Maybe.fromCallable { departments.firstOrNull { it.connectEnrollmentSupported } ?: departments.firstOrNull() }
            }
    }

    private fun getPostalCodeCode(): Single<String> {
        return registrationManager.getRegistrationData()
            .map { it.postalCode!! }
    }

    private fun postalCodeUsagePermissionGranted(): Single<Boolean> {
        // TODO: 24.01.22 connect with consent for luca Connect
        return Single.just(false)
    }

}

private const val RESPONSIBLE_HEALTH_DEPARTMENT_KEY = "responsible_health_department"
private val MINIMUM_UPDATE_DELAY = TimeUnit.DAYS.toMillis(1)