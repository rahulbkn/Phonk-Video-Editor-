package dev.phonk.editor.e2e

import org.junit.Assert.assertEquals
import org.junit.Test

/** Smoke gate: verifies the E2E runner is wired. `compute` must return 5. */
class E2EGateTest {

    @Test
    fun gateHolds() {
        assertEquals("E2E gate expected value", 5, E2EGate.compute())
    }
}

object E2EGate {
    fun compute(): Int = 5
}
