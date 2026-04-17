# SuperSherpa

SuperSherpa is an Android speech-to-text prototype built with Kotlin, Jetpack Compose, and a Rust native transcription core. The long-term product direction is an offline floating mic experience, but the repository's current app build is centered on a recorder flow plus a custom keyboard integration.

## Current State

What is implemented today:

- Compose app with three main screens: recorder, history, and settings
- JNI bridge into `transcribe-rs`, which owns microphone capture and transcription
- Live recorder states in the UI: `Idle`, `Listening`, `Processing`, `Result`, and `Error`
- Copy-to-clipboard from the recorder result and saved history
- Local transcript history stored with Room
- Custom IME (`TranscriptionImeService`) with start, stop, transcribe, and paste-back behavior
- OTA model install flow for the pinned `parakeet-tdt-0.6b-v3-int8` package
- Rust native library packaged into the Android app for `arm64-v8a`

What is not implemented yet:

- Floating overlay bubble/service
- Foreground overlay microphone flow outside the app/IME experience
- Manifest/service wiring for `SYSTEM_ALERT_WINDOW`-based overlay behavior

## How It Works

Current runtime flow:

`Mic -> Rust native layer -> transcription/status callbacks -> ViewModel/UI state -> Compose screens or IME`

Key pieces:

- `app/`: Android UI, state, permissions, history, IME, and model delivery
- `transcribe-rs/`: Rust audio/transcription engine and Android bridge
- `app/src/main/jniLibs/arm64-v8a/`: packaged native `.so` files
- `app/src/main/assets/model_delivery/manifest.json`: pinned model manifest used by the OTA installer

One important detail: transcription is local after a model is installed, but the current app includes `INTERNET` because model download is handled in-app when the model is missing.

## Android App Snapshot

- Package: `com.sublime.supersherpa`
- App name in the current build: `SuperSherpa`
- Min SDK: 26
- Target SDK: 36
- UI stack: Jetpack Compose + Material 3
- Persistence: Room
- Native runtime: Rust + ONNX Runtime Android

## Permissions In The Current Manifest

- `android.permission.RECORD_AUDIO`
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_MICROPHONE`
- `android.permission.INTERNET`

`SYSTEM_ALERT_WINDOW` is not currently declared because the overlay service is still not present in the app.

## Build Notes

The Android build triggers a Rust native build from `transcribe-rs/` before `preBuild`, so local builds need the usual Android toolchain plus Rust tooling available for `cargo ndk`.

Build the debug app:

```bash
./gradlew assembleDebug
```

Run JVM tests:

```bash
./gradlew testDebugUnitTest
```

Run instrumentation tests:

```bash
./gradlew connectedDebugAndroidTest
```

Run lint:

```bash
./gradlew lintDebug
```

## Tests Present In The Repo

- JVM tests for `TranscriptionViewModel`, `VoiceState`, transcript history, and OTA model delivery
- Basic Android instrumentation scaffold under `app/src/androidTest/`
- Rust tests under `transcribe-rs/tests/`

## Screenshots

### Light Theme

<p>
  <img src="screenshots/light/recorder.png" alt="Recorder (Light)" width="19%" />
  <img src="screenshots/light/history.png" alt="History (Light)" width="19%" />
  <img src="screenshots/light/settings.png" alt="Settings (Light)" width="19%" />
  <img src="screenshots/light/keyboard_light.png" alt="Keyboard (Light)" width="19%" />
  <img src="screenshots/light/keyboard_light_output.png" alt="Keyboard Output (Light)" width="19%" />
</p>

### Dark Theme

<p>
  <img src="screenshots/dark/recorder.png" alt="Recorder (Dark)" width="19%" />
  <img src="screenshots/dark/history.png" alt="History (Dark)" width="19%" />
  <img src="screenshots/dark/settings.png" alt="Settings (Dark)" width="19%" />
  <img src="screenshots/dark/keyboard_dark.png" alt="Keyboard (Dark)" width="19%" />
  <img src="screenshots/dark/keyboard_dark_output.png" alt="Keyboard Output (Dark)" width="19%" />
</p>

## License

See [LICENSE](LICENSE).
