# Phonk Drop Editor

Beat-synchronized video cutting for phonk edits: real audio analysis (C++ DSP
via JNI), beat-aligned auto-cuts, drop-aware effects (flash/zoom/shake) and
FFmpeg rendering — everything computed deterministically from the audio, never
guessed.

- Real detection. BPM, beats, downbeats and drops come from DSP on the actual
  track (STFT, onset envelope, autocorrelation, multi-signal drop scoring).
  No random or hardcoded markers.
- All timestamps in **ms**; deterministic analysis with confidence scores.
- If no strong drop is detected the UI says:
  *"No strong drop detected. You can manually add a drop."*
- Low-RAM: audio is decoded to 11 025 Hz mono PCM in bounded chunks and never
  loaded whole; analysis truncates at 15 minutes.

## Layout

```
app/src/main/
  cpp/                       native DSP + JNI (headless, testable)
    analysis.cpp             STFT, features, BPM, beats, downbeats, drops
    json.cpp                 minimal JSON DOM (parse/stringify/escape)
    timeline_engine.cpp      beat-synchronized cut plan
    phonk_jni.cpp            JNI surface (nativeAnalyzeAudio, ...)
  java/dev/phonk/editor/
    native/PhonkNative.kt    JNI calls
    analysis/                MediaCodec decode -> PCM, JSON parser, state machine
    model/                   AnalysisResult / DropType / ClipSegment / ExportConfig
    editor/                  CutPlanner (patterns A..F), EditEngine (undo/redo)
    ffmpeg/                  FFmpegCommandBuilder (argv only, no shell) + renderer
    export/                  ExportRunner (render -> MediaStore)
    timeline/                TimelineController + pan/zoom custom View
    ui/                      Compose screens (Home / Editor / Export)
    crash/                   crash capture + crash-log screen
python/
  analysis/analysis.py       offline twin of the C++ pipeline (numpy/scipy)
  analysis/synthesize.py     synthetic phonk generator for tests
  tests/test_analysis.py     ground-truth assertions (140 BPM, 8s drop, schema)
scripts/
  fetch-ffmpeg.sh            optional host/Termux ffmpeg for tests
  run-python-analysis.sh     python smoke tests + CLI
```

## Build (Termux)

Requirements (`setup-android-toolchain.sh` installs these): `openjdk-17`,
`gradle` (9.7.0), `ninja`, `cmake`, `aarch64-linux-android` binutils, an arm64
`aapt2` (ReVanced build) placed at `$HOME/android-sdk/build-tools/36.0.0/aapt2`,
and a minimal fake NDK tree at `$HOME/android-sdk/ndk/27.1.12297006` that
delegates to Termux clang with `--target=aarch64-linux-android24`.

```
./build-apk.sh            # gradle assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
# install: adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Why no real NDK: `sdkmanager` is unavailable on Termux, so the build pins
`ndkVersion = "27.1.12297006"` to the fake tree. AGP only touches the NDK for
the compiler/sysroot paths, which the fake `android.toolchain.cmake` supplies
(AGP's CMake File API parser requires a `link.sysroot`, hence `CMAKE_SYSROOT`).

## Tests

```
gradle :app:testDebugUnitTest     # JVM: planner, codec, ffmpeg args, edits, crash
(cd python && python3 -m tests.test_analysis)   # DSP twin vs synthetic ground truth
```

## Rendering

The app does not bundle ffmpeg (keeps the APK small). To enable exports, drop
an arm64 ffmpeg binary into `$filesDir/ffmpeg/ffmpeg` (see
`FfmpegRenderer.kt`); otherwise the export UI shows a clear "FFmpeg not
bundled" message. All commands are built as argv arrays and executed with
`ProcessBuilder` — no shell interpretation, so media paths are safe.

## Known limitations

- Analysis target: 11 025 Hz mono; max 15 minutes.
- Export requires a drop-in ffmpeg binary (documented above).
- Fake NDK is a skeleton; only arm64-v8a is targeted (`abiFilters`).
