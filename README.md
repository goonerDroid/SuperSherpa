# SuperSherpa

SuperSherpa is an Android offline speech-to-text app built with Kotlin, Jetpack Compose, and a Rust native transcription core.

## Current App State

Implemented now:

- Offline transcription pipeline via JNI into `transcribe-rs`
- Recorder screen with live voice phases (`Idle`, `Listening`, `Processing`, `Result`, `Error`)
- Copy-to-clipboard from the recorder and history screens
- Local transcript history persisted with Room
- Settings screen for microphone permission + IME setup
- Custom IME (`TranscriptionImeService`) with voice bar start/stop/paste flow
- Material 3 Compose UI with screen transitions

Not implemented yet:

- Floating overlay service (`VoiceOverlayService`) is still planned and not active in the current app build

## Architecture Snapshot

- Android app: Kotlin, Compose, MVVM-style presentation state
- Native layer: Rust (`transcribe-rs`) loaded as `libtranscribe_rs.so`
- Native bridge: `RustTranscriptionBridge`
- Persistence: Room (`TranscriptHistoryRepository`)

Pipeline today:

`Mic -> Rust native recording/inference -> status + transcript callbacks -> ViewModel state -> Compose UI`

## Screenshots

### Light Theme

![Recorder (Light)](screenshots/light/recorder.png)
![History (Light)](screenshots/light/history.png)
![Settings (Light)](screenshots/light/settings.png)
![Keyboard (Light)](screenshots/light/keyboard_light.png)
![Keyboard Output (Light)](screenshots/light/keyboard_light_output.png)

### Dark Theme

![Recorder (Dark)](screenshots/dark/recorder.png)
![History (Dark)](screenshots/dark/history.png)
![Settings (Dark)](screenshots/dark/settings.png)
![Keyboard (Dark)](screenshots/dark/keyboard_dark.png)
![Keyboard Output (Dark)](screenshots/dark/keyboard_dark_output.png)

## Permissions

Declared in `AndroidManifest.xml`:

- `android.permission.RECORD_AUDIO`
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_MICROPHONE`
- `android.permission.SYSTEM_ALERT_WINDOW`

## Build

```bash
./gradlew assembleDebug
```

## Tests

```bash
./gradlew testDebugUnitTest
```

```bash
./gradlew connectedDebugAndroidTest
```

```bash
./gradlew lintDebug
```

## Project Layout (Current)

- `app/` Android client (Compose UI, ViewModel, Room, IME service)
- `transcribe-rs/` Rust transcription core + Android bridge
- `model_assets/` large model asset pack module
- `screenshots/` README screenshots (light + dark)

## License

See [LICENSE](LICENSE).
