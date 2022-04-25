package de.culture4life.luca.consent

import android.content.Context
import de.culture4life.luca.Manager
import de.culture4life.luca.preference.PreferencesManager
import de.culture4life.luca.util.TimeUtil
import de.culture4life.luca.util.addTo
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import timber.log.Timber

open class ConsentManager(
    private val preferencesManager: PreferencesManager
) : Manager() {

    private val consentSubjects: MutableMap<String, BehaviorSubject<Consent>> = HashMap()
    private val consentRequestsSubject: PublishSubject<String> = PublishSubject.create()

    override fun doInitialize(context: Context): Completable {
        return preferencesManager.initialize(context)
    }

    override fun dispose() {
        consentSubjects.clear()
        super.dispose()
    }

    fun getConsentRequests(): Observable<String> {
        return consentRequestsSubject
    }

    fun getConsent(id: String): Single<Consent> {
        return getOrCreateConsentSubject(id).firstOrError()
    }

    fun getConsentAndChanges(id: String): Observable<Consent> {
        return Observable.defer { getOrCreateConsentSubject(id) }
    }

    fun persistConsent(consent: Consent): Completable {
        return preferencesManager.persist(getPreferenceKey(consent.id), consent)
            .doOnComplete { getOrCreateConsentSubject(consent.id).onNext(consent) }
            .doOnSubscribe { Timber.v("Persisting consent: $consent") }
    }

    fun assertConsentApproved(id: String): Completable {
        return getConsent(id)
            .flatMapCompletable { assertConsentApproved(it) }
    }

    fun assertConsentApproved(consent: Consent): Completable {
        return Completable.defer {
            if (consent.approved) {
                Completable.complete()
            } else {
                Completable.error(MissingConsentException(consent.id))
            }
        }.doOnSubscribe { Timber.v("Asserting that consent is approved: $consent") }
    }

    fun requestConsentIfRequiredAndAssertApproved(id: String): Completable {
        return requestConsentIfRequiredAndGetResult(id)
            .flatMapCompletable { assertConsentApproved(it) }
    }

    fun requestConsentIfRequiredAndGetResult(id: String): Single<Consent> {
        return getConsent(id)
            .flatMap {
                assertConsentApproved(id)
                    .andThen(Single.just(it))
                    .onErrorResumeWith(requestConsentAndGetResult(id))
            }
    }

    fun requestConsentAndGetResult(id: String): Single<Consent> {
        return requestConsent(id)
            .andThen(getConsentAndChanges(id))
            .skip(1)
            .firstOrError()
    }

    fun requestConsentIfRequired(id: String): Completable {
        return assertConsentApproved(id)
            .onErrorResumeWith(requestConsent(id))
    }

    fun requestConsent(id: String): Completable {
        return Completable.fromAction { consentRequestsSubject.onNext(id) }
            .doOnSubscribe { Timber.d("Requesting consent for: $id") }
    }

    fun processConsentRequestResult(id: String, approved: Boolean): Completable {
        return getConsent(id)
            .map {
                it.copy(
                    approved = approved,
                    lastDisplayTimestamp = TimeUtil.getCurrentMillis()
                )
            }
            .flatMapCompletable(::persistConsent)
            .doOnSubscribe { Timber.d("Processing consent result for: $id: $approved") }
    }

    private fun getPreferenceKey(id: String): String {
        return KEY_CONSENT_PREFIX + id
    }

    private fun getOrCreateConsentSubject(id: String): BehaviorSubject<Consent> {
        var subject = consentSubjects[id]
        if (subject == null) {
            subject = BehaviorSubject.create()
            consentSubjects[id] = subject
            preferencesManager.restoreOrDefault(getPreferenceKey(id), Consent(id))
                .doOnSuccess(subject::onNext)
                .subscribeOn(Schedulers.io())
                .subscribe()
                .addTo(managerDisposable)
        }
        return subject!!
    }

    companion object {
        const val ID_TERMS_OF_SERVICE_LUCA_ID = "terms_of_service_luca_id" // March 2022, version code 96
        const val ID_ENABLE_CAMERA = "enable_camera"
        const val ID_IMPORT_DOCUMENT = "import_document"
        const val ID_INCLUDE_ENTRY_POLICY = "include_entry_policy"
        const val ID_POSTAL_CODE_MATCHING = "postal_code_matching"
        const val ID_OPEN_VENUE_URL = "open_venue_url"
        private const val KEY_CONSENT_PREFIX = "consent_"
    }
}
