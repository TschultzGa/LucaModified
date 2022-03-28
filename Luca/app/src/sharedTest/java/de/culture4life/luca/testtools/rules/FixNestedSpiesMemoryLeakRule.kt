package de.culture4life.luca.testtools.rules

import org.mockito.Mockito

class FixNestedSpiesMemoryLeakRule : BaseHookingTestRule() {
    override fun beforeTest() {}

    override fun afterTest() {
        // Nested spies produce memory leaks.
        // https://github.com/mockito/mockito/issues/1532
        // https://github.com/mockito/mockito/issues/1533
        Mockito.framework().clearInlineMocks()
    }
}
