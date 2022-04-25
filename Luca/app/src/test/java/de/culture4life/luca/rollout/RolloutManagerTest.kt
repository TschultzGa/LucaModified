package de.culture4life.luca.rollout

import de.culture4life.luca.LucaUnitTest
import io.reactivex.rxjava3.core.Observable
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.whenever

class RolloutManagerTest : LucaUnitTest() {

    private val rolloutManager = Mockito.spy(getInitializedManager(application.rolloutManager))

    @Test
    fun `Rollout disabled by default`() {
        rolloutManager.isRolledOutToThisDevice("non-existent-feature")
            .test()
            .assertValue(false)
    }

    @Test
    fun `Rollout disabled if configuration forbids it`() {
        val configuration = RolloutConfigurationTest.CONFIGURATION_ROLLOUT_NOT_YET_STARTED
        givenUpdatedConfiguration(configuration)

        rolloutManager.isRolledOutToThisDevice(configuration.id)
            .test()
            .assertValue(false)
    }

    @Test
    fun `Rollout enabled if configuration allows it`() {
        val configuration = RolloutConfigurationTest.CONFIGURATION_ROLLOUT_COMPLETED
        givenUpdatedConfiguration(configuration)

        rolloutManager.isRolledOutToThisDevice(configuration.id)
            .test()
            .assertValue(true)
    }

    @Test
    fun `Changed rollout ratio updates configuration`() {
        val initialConfiguration = RolloutConfigurationTest.CONFIGURATION_ROLLOUT_NOT_YET_STARTED
        val updatedConfiguration = initialConfiguration.copy(rolloutRatio = RolloutConfiguration.MAXIMUM_ROLLOUT_RATIO)

        givenUpdatedConfiguration(initialConfiguration)

        val configurationChangeObserver = rolloutManager.getConfigurationChanges(initialConfiguration.id)
            .map { it.rolloutRatio }
            .test()
            .assertNoValues()

        givenUpdatedConfiguration(updatedConfiguration)

        configurationChangeObserver.assertValue(updatedConfiguration.rolloutRatio)
        rolloutManager.getConfiguration(initialConfiguration.id)
            .map { it.rolloutRatio }
            .test()
            .assertValue(updatedConfiguration.rolloutRatio)
    }

    @Test
    fun `Constant rollout ratio does not update configuration`() {
        val initialConfiguration = RolloutConfigurationTest.CONFIGURATION_ROLLOUT_NOT_YET_STARTED
        val updatedConfiguration = initialConfiguration.copy()

        givenUpdatedConfiguration(initialConfiguration)

        val configurationChangeObserver = rolloutManager.getConfigurationChanges(initialConfiguration.id)
            .test()
            .assertNoValues()

        givenUpdatedConfiguration(updatedConfiguration)

        configurationChangeObserver.assertNoValues()
    }

    private fun givenUpdatedConfiguration(remoteConfiguration: RolloutConfiguration) {
        whenever(rolloutManager.fetchRemoteConfigurations()).thenReturn(Observable.just(remoteConfiguration))
        rolloutManager.updateRolloutRatios().blockingAwait()
    }
}
