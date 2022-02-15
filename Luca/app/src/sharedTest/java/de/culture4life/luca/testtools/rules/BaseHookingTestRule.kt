package de.culture4life.luca.testtools.rules

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

abstract class BaseHookingTestRule : TestRule {

    abstract fun beforeTest()
    abstract fun afterTest()


    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                beforeTest()
                try {
                    return base.evaluate()
                } finally {
                    afterTest()
                }
            }
        }
    }
}