# Attempt 1 — E2E gate fix

## Root cause

`E2EGate.compute()` in
`app/src/test/java/dev/phonk/editor/e2e/E2EGateTest.kt` was hardcoded to return
`4`, while `E2EGateTest.gateHolds()` asserts `assertEquals("E2E gate expected
value", 5, E2EGate.compute())`. This is the deliberately-injected
copy-paste/miscalculation error described in the test's own comment: the
reference/expected value is 5, but `compute()` returns 4, so the assertion
fails with `expected:<5> but was:<4>`. No application code, engine logic, or
other feature is involved — this is purely the injected gate value.

## Fix

The smallest safe fix is to make `compute()` return the expected value 5
(matching the assertion and the test's stated intent) instead of 4:

```kotlin
object E2EGate {
    fun compute(): Int = 5
}
```

No tests were removed, disabled, or weakened; no lint/static checks or CI
workflow files were touched. The rest of the codebase is unchanged.

## Verification

- `./gradlew :app:testDebugUnitTest --tests "dev.phonk.editor.e2e.E2EGateTest"`
  → BUILD SUCCESSFUL (1 test, 0 failed).
- `./gradlew :app:testDebugUnitTest` (full suite)
  → BUILD SUCCESSFUL, 88 tests, 0 failed, 0 errors, 0 skipped.