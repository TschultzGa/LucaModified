package de.culture4life.luca.testtools.rules

import de.culture4life.luca.LucaApplication
import net.lachlanmckee.timberjunit.TimberTestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Setup and cleanup of printing log statements.
 */
class LoggingRule : BaseHookingTestRule() {

    // Should NOT be enabled by default because it delays the test execution.
    private val isEnabled = false

    // Prints extra stuff like FragmentManager logs.
    private val isEnabledForRobolectric = false

    private val timberRule = TimberTestRule.logAllAlways()

    override fun apply(base: Statement, description: Description): Statement {
        var wrappedStatement = base
        if (isEnabled) {
            wrappedStatement = timberRule.apply(base, description)
        }
        return super.apply(wrappedStatement, description)
    }

    override fun beforeTest() {
        if (isEnabledForRobolectric) {
            printLogsForRobolectricTests()
        }
    }

    override fun afterTest() {}

    private fun printLogsForRobolectricTests() {
        if (LucaApplication.isRunningUnitTests()) {
            // Done through reflection to keep it compilable for androidTest target.
            val shadowLog = javaClass.classLoader!!.loadClass("org.robolectric.shadows.ShadowLog")
            shadowLog.getDeclaredField("stream").set(shadowLog, System.out)
        }
    }
}