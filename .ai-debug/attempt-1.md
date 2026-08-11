# Attempt 1 — E2E gate fix

## Root cause

Commit `10046f1` ("test(e2e): inject deterministic failing E2EGateTest") deliberately
introduced a copy-paste error in `app/src/test/java/dev/phonk/editor/e2e/E2EGateTest.kt`:
`E2EGate.compute()` returned `4` while the test asserted the expected value `5`. This made
`E2EGateTest.gateHolds` fail with `java.lang.AssertionError: E2E gate expected value
expected:<5> but was:<4>`. The `E2EGate` object is defined only inside this test file and
has no production callers, so the wrong return value was a pure test-fixture defect with no
functional impact on the app.

## Fix

The minimal safe fix was to change the injected wrong return value to the expected one:

```kotlin
fun compute(): Int = 5   // was: 4
```

No tests were deleted, disabled, or weakened; no other source files, CI workflows, or build
configuration were touched.

## Verification

Ran the local unit-test gate:

```
./gradlew :app:testDebugUnitTest --console=plain
```

Result: `BUILD SUCCESSFUL in 4m 34s`. `TEST-dev.phonk.editor.e2e.E2EGateTest.xml` reports
`tests="1" failures="0" errors="0"`, i.e. `gateHolds` now passes, and all other unit tests
continue to pass.

## Current state

- Branch: feature/ai-fix-e2e
- Build: SUCCESS (all debug unit tests green, including the previously failing E2EGateTest)
