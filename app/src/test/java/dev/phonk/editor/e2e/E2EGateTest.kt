package dev.phonk.editor.e2e

import org.junit.Assert.assertEquals
import org.junit.Test

/** Deterministic failure injected to exercise the autonomous AI debug loop.
 *
 * `compute` is deliberately wrong (returns 4 instead of 5). The AI debugger
 * must find the root cause (a copy-paste error) and make the smallest safe
 * fix. This test will keep failing until `compute` returns 5.
 */
class E2EGateTest {

    @Test
    fun gateHolds() {
        assertEquals("E2E gate expected value", 5, E2EGate.compute())
    }
}

object E2EGate {
    fun compute(): Int = 5
}
