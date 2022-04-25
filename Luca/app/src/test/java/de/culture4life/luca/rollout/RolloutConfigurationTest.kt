package de.culture4life.luca.rollout

import de.culture4life.luca.LucaUnitTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RolloutConfigurationTest : LucaUnitTest() {

    @Test
    fun `Rollout disabled if rollout ratio is zero`() {
        assertFalse(CONFIGURATION_ROLLOUT_NOT_YET_STARTED.rolloutStarted)
        assertFalse(CONFIGURATION_ROLLOUT_NOT_YET_STARTED.rolloutToThisDevice)
        assertFalse(CONFIGURATION_ROLLOUT_NOT_YET_STARTED.rolloutCompleted)
    }

    @Test
    fun `Rollout disabled if rollout ratio below gambled ratio`() {
        assertTrue(CONFIGURATION_ROLLOUT_DISABLED.rolloutStarted)
        assertFalse(CONFIGURATION_ROLLOUT_DISABLED.rolloutToThisDevice)
        assertFalse(CONFIGURATION_ROLLOUT_DISABLED.rolloutCompleted)
    }

    @Test
    fun `Rollout enabled if rollout ratio above gambled ratio`() {
        assertTrue(CONFIGURATION_ROLLOUT_ENABLED.rolloutStarted)
        assertTrue(CONFIGURATION_ROLLOUT_ENABLED.rolloutToThisDevice)
        assertFalse(CONFIGURATION_ROLLOUT_ENABLED.rolloutCompleted)
    }

    @Test
    fun `Rollout enabled if rollout ratio is one`() {
        assertTrue(CONFIGURATION_ROLLOUT_COMPLETED.rolloutStarted)
        assertTrue(CONFIGURATION_ROLLOUT_COMPLETED.rolloutToThisDevice)
        assertTrue(CONFIGURATION_ROLLOUT_COMPLETED.rolloutCompleted)
    }

    @Test
    fun `Rollout ratio equality check tolerates rounding errors`() {
        assertTrue(CONFIGURATION_ROLLOUT_NOT_YET_STARTED.rolloutRatioEquals(0F))
        assertTrue(CONFIGURATION_ROLLOUT_NOT_YET_STARTED.rolloutRatioEquals(0.000001F))
    }

    companion object {
        val CONFIGURATION_ROLLOUT_NOT_YET_STARTED = RolloutConfiguration(
            id = "not-yet-started-rollout-feature",
            rolloutRatio = 0F,
            gambledRatio = 0F
        )
        val CONFIGURATION_ROLLOUT_DISABLED = RolloutConfiguration(
            id = "not-yet-rolled-out-feature",
            rolloutRatio = 0.25F,
            gambledRatio = 0.5F
        )
        val CONFIGURATION_ROLLOUT_ENABLED = RolloutConfiguration(
            id = "rolled-out-feature",
            rolloutRatio = 0.75F,
            gambledRatio = 0.5F
        )
        val CONFIGURATION_ROLLOUT_COMPLETED = RolloutConfiguration(
            id = "completed-rollout-feature",
            rolloutRatio = 1F,
            gambledRatio = 0.5F
        )
    }
}
