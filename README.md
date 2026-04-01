# SuperSherpa

SuperSherpa is an Android app for fully offline, on-device speech transcription. The product direction is a Superwhisper-style MVP with a floating mic overlay, real-time transcription, and local-only processing.

## Current Stack

- Kotlin
- Jetpack Compose
- Material 3
- Jetpack Compose adaptive navigation
- Room
- Koin
- Coroutines
- Sherpa-ONNX via local AAR

## Current App Shape

The project is set up as a single Android app module:

- App package: `com.sublime.supersherpa`
- Main entry point: `app/src/main/java/com/sublime/supersherpa/MainActivity.kt`
- Minimum SDK: 26
- Target SDK: 36
- Compile SDK: 36

The current codebase already includes:

- Compose UI scaffolding
- Voice state model types
- Room database and repository pieces
- Basic test coverage
- Local Sherpa-ONNX dependency wiring

## MVP Goals

The intended offline transcription pipeline is:

`Mic -> AudioRecord -> SherpaEngine -> Real-time transcription -> Floating UI`

Planned core features:

- Floating mic overlay
- On-device speech-to-text
- Streaming transcription
- Copy transcription to clipboard
- Fully offline operation

## Project Structure

New code is expected to live under these areas:

- `app/src/main/java/com/sublime/supersherpa/core/audio/`
- `app/src/main/java/com/sublime/supersherpa/core/ai/`
- `app/src/main/java/com/sublime/supersherpa/core/overlay/`
- `app/src/main/java/com/sublime/supersherpa/core/permissions/`
- `app/src/main/java/com/sublime/supersherpa/feature/transcription/`
- `app/src/main/java/com/sublime/supersherpa/ui/floating/`
- `app/src/main/java/com/sublime/supersherpa/model/`

## Voice State

The shared UI state contract is centered around `VoiceState`:

- `Idle`
- `Listening`
- `Processing`
- `Result(text)`
- `Error(message)`

## Sherpa-ONNX Setup

The app expects a local Sherpa AAR in:

- `app/libs/sherpa-onnx-1.12.34.aar`

Expected model assets for the MVP live under:

- `app/src/main/assets/models/encoder.onnx`
- `app/src/main/assets/models/decoder.onnx`
- `app/src/main/assets/models/tokens.txt`

## Permissions

The overlay and transcription flow are expected to use:

- `android.permission.RECORD_AUDIO`
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.SYSTEM_ALERT_WINDOW`
- `android.permission.POST_NOTIFICATIONS`

## Build

From the project root:

```bash
./gradlew assembleDebug
```

## Test

Run unit tests:

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

## Notes

- Audio work should stay on `Dispatchers.IO`.
- AI inference should stay on `Dispatchers.Default`.
- UI updates should run on the main thread.
- The overlay service should remain available after the host activity closes, subject to Android platform limits.
- The MVP should stay offline-first and avoid cloud dependencies.

## License

See [LICENSE](LICENSE).
