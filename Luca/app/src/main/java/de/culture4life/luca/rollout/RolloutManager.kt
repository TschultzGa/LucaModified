package de.culture4life.luca.rollout

import android.content.Context
import androidx.annotation.VisibleForTesting
import de.culture4life.luca.LucaApplication
import de.culture4life.luca.Manager
import de.culture4life.luca.network.NetworkManager
import de.culture4life.luca.preference.PreferencesManager
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.PublishSubject
import timber.log.Timber
import java.util.concurrent.TimeUnit

open class RolloutManager(
    private val preferencesManager: PreferencesManager,
    private val networkManager: NetworkManager
) : Manager() {

    private val configurationSubject = PublishSubject.create<RolloutConfiguration>()

    override fun doInitialize(context: Context): Completable {
        return Completable.mergeArray(
            preferencesManager.initialize(context),
            networkManager.initialize(context)
        ).andThen(invokeUpdateRolloutRatiosIfRequired())
    }

    fun isRolledOutToThisDevice(id: String): Single<Boolean> {
        return getConfiguration(id)
            .map { it.rolloutToThisDevice }
    }

    fun assertRolledOutToThisDevice(id: String): Completable {
        return isRolledOutToThisDevice(id)
            .flatMapCompletable {
                if (it) {
                    Completable.complete()
                } else {
                    Completable.error(RolloutException(id))
                }
            }
    }

    /*
        Rollout ratio updates
     */

    private fun invokeUpdateRolloutRatiosIfRequired(): Completable {
        return if (LucaApplication.isRunningTests()) {
            Completable.complete()
        } else {
            invokeDelayed(updateRolloutRatios(), TimeUnit.SECONDS.toMillis(3))
        }
    }

    fun updateRolloutRatios(): Completable {
        return fetchRemoteConfigurations()
            .flatMapCompletable(this::handleRemoteConfiguration)
            .doOnSubscribe { Timber.d("Updating rollout ratios") }
            .doOnError { Timber.w("Unable to update rollout ratios: $it") }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun fetchRemoteConfigurations(): Observable<RolloutConfiguration> {
        return networkManager.getLucaEndpointsV4()
            .flatMap { it.rolloutRatios }
            .flatMapObservable { Observable.fromIterable(it) }
            .map(::RolloutConfiguration)
    }

    private fun handleRemoteConfiguration(remoteConfiguration: RolloutConfiguration): Completable {
        return getConfiguration(remoteConfiguration.id)
            .flatMapCompletable { handleConfigurationUpdate(it, remoteConfiguration) }
    }

    private fun handleConfigurationUpdate(localConfiguration: RolloutConfiguration, remoteConfiguration: RolloutConfiguration): Completable {
        return Completable.defer {
            if (localConfiguration.rolloutRatioEquals(remoteConfiguration.rolloutRatio)) {
                Timber.v("Rollout configuration unchanged: $localConfiguration")
                Completable.complete()
            } else {
                val updatedConfiguration = localConfiguration.copy(rolloutRatio = remoteConfiguration.rolloutRatio)
                Timber.i("Rollout configuration updated: $updatedConfiguration")
                persistConfiguration(updatedConfiguration)
            }
        }
    }

    /*
        Configuration
     */

    fun getConfiguration(id: String): Single<RolloutConfiguration> {
        return restoreConfigurationIfAvailable(id)
            .switchIfEmpty(Single.fromCallable { RolloutConfiguration(id) })
    }

    fun getConfigurationChanges(id: String): Observable<RolloutConfiguration> {
        return configurationSubject
            .filter { it.id == id }
            .distinctUntilChanged()
    }

    private fun restoreConfigurationIfAvailable(id: String): Maybe<RolloutConfiguration> {
        return preferencesManager.restoreIfAvailable(getPreferenceKey(id), RolloutConfiguration::class.java)
    }

    private fun persistConfiguration(configuration: RolloutConfiguration): Completable {
        return preferencesManager.persist(getPreferenceKey(configuration.id), configuration)
            .doOnComplete { configurationSubject.onNext(configuration) }
    }

    private fun getPreferenceKey(id: String): String {
        return "rollout_configuration_$id"
    }

    companion object {
        const val ID_LUCA_ID_ENROLLMENT = "luca-id"
    }
}
