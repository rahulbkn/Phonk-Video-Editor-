#!/data/data/com.termux/files/usr/bin/env bash
# Build the Phonk Drop Editor debug APK from Termux.
#
# Requirements (installed by setup-android-toolchain.sh):
#   openjdk-17, gradle, ninja, cmake, aarch64-linux-android binutils/libc++
#   arm64 aapt2 at $HOME/android-sdk/build-tools/36.0.0/aapt2
#
# The build intentionally uses:
#   - AGP 9.3.1 with the bundled Kotlin compiler (no kotlin.android plugin)
#   - a fake NDK tree at $HOME/android-sdk/ndk/27.1.12297006 that delegates to
#     Termux clang with --target=aarch64-linux-android24 --sysroot=...
#   - gradle 9.7.0 (org.gradle.daemon=false to stay Termux-friendly)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

echo "[phonk] verifying toolchain..."
if ! command -v gradle >/dev/null 2>&1; then
  echo "ERROR: gradle not found. Run: pkg install gradle"
  exit 1
fi
test -x "$HOME/android-sdk/build-tools/36.0.0/aapt2" || {
  echo "ERROR: arm64 aapt2 missing. Run setup-android-toolchain.sh first."
  exit 1
}

echo "[phonk] gradle assembleDebug (first run may take several minutes)..."
gradle assembleDebug --no-daemon

APK="app/build/outputs/apk/debug/app-debug.apk"
if [[ -f "$APK" ]]; then
  echo "[phonk] OK: $APK"
  echo "[phonk] install with: adb install -r $APK"
else
  echo "[phonk] FAILED: no APK produced"
  exit 1
fi
