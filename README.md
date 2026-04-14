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
