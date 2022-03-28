package de.culture4life.luca.testtools.rules

/**
 * For debugging memory leaks.
 *
 * Sample use case:
 * - Put a breakpoint at the the last executed test method. After reaching it
 *   create a heap dump with VisualVM. Then watch out for leaks.
 */
// TODO Improve by measure before LucaApplication onCreate and after onTerminate.
class MemoryUsageRule : BaseHookingTestRule() {

    // Should NOT be enabled by default because it delays the test execution.
    private val isEnabled = false

    override fun beforeTest() {
        if (isEnabled) {
            callGarbageCollector()
            printMemoryUsage("before")
        }
    }

    override fun afterTest() {
        if (isEnabled) {
            callGarbageCollector()
            printMemoryUsage("after ") // extra space to align start to end outputs
        }
    }

    private fun callGarbageCollector() {
        System.gc()
        // Usually calling the garbage collector does not ensure, that he will run and cleanup all stuff.
        // System.gc() doc "... method call, the Java Virtual Machine has made a best effort ..."
        // Do it twice helps to cleanup much more to remove objects without any GC Root from the memory.
        System.gc()
    }

    private fun printMemoryUsage(marker: String) {
        println(
            with(Runtime.getRuntime()) {
                "${MemoryUsageRule::class.simpleName} $marker -" +
                    " used: ${unify((totalMemory() - freeMemory()))}" +
                    " total: ${unify(totalMemory())}" +
                    " max: ${unify(maxMemory())}"
            }
        )
    }

    private fun unify(number: Long) = (number / 1024 / 1024).toString().padStart(4, ' ').plus(" (MB)")
}
