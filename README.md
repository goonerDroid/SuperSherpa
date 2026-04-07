# SuperSherpa

SuperSherpa is a fully offline Android speech transcription app built with Kotlin, Jetpack Compose, and a Rust backend.

It is designed for fast, private, on-device transcription with a modern mobile UI and a lightweight architecture that keeps inference separate from the Android frontend.

## What It Does

SuperSherpa provides:

- floating mic transcription
- real-time speech-to-text
- clipboard copy support
- fully offline operation
- local-only audio processing
- a Compose-first Android experience

## Why This Approach

SuperSherpa uses a Kotlin + Compose Android client and a Rust transcription backend.

This gives the app:

- a modern Android UI stack
- clean separation between UI and inference
- strong performance for audio and model handling
- the ability to use the latest Parakeet models locally
- a backend that can evolve independently of the Android app

## Tech Stack

- Kotlin
- Jetpack Compose
- Material 3
- Navigation Compose
- ViewModel
- StateFlow
- Coroutines
- DataStore
- Room
- Rust backend for transcription
- JNI bridge between Kotlin and Rust

## Architecture

The app follows a modern Android architecture:

- Compose for UI
- ViewModel for screen state
- StateFlow for observable state
- repositories for data and native access
- background work on coroutines
- Rust for audio inference and transcription

Pipeline:

`Mic -> AudioRecord -> Rust backend -> Parakeet model -> Transcription -> UI`

## MVP Goals

The initial release focuses on:

- floating mic overlay
- on-device speech-to-text
- real-time transcription
- copy to clipboard
- offline-first operation
- clean and responsive Compose UI
- native backend integration through Rust

## Voice State

The shared UI state model is centered around `VoiceState`:

- `Idle`
- `Listening`
- `Processing`
- `Result(text)`
- `Error(message)`

## Project Structure

Planned code areas:

- `app/src/main/java/com/sublime/supersherpa/core/audio/`
- `app/src/main/java/com/sublime/supersherpa/core/ai/`
- `app/src/main/java/com/sublime/supersherpa/core/overlay/`
- `app/src/main/java/com/sublime/supersherpa/core/permissions/`
- `app/src/main/java/com/sublime/supersherpa/feature/transcription/`
- `app/src/main/java/com/sublime/supersherpa/ui/floating/`
- `app/src/main/java/com/sublime/supersherpa/model/`
- `rust/` or `native/` for the Rust backend

## Rust Backend

The Rust layer handles:

- Parakeet model loading
- transcription inference
- streaming and batch transcription logic
- audio pipeline behavior
- JNI callbacks back into Kotlin

The goal is to keep the transcription engine small, fast, and independent from UI concerns.

## Model Setup

SuperSherpa is intended to use the latest Parakeet models locally.

Expected model assets live under:

- `app/src/main/assets/models/encoder.onnx`
- `app/src/main/assets/models/decoder.onnx`
- `app/src/main/assets/models/tokens.txt`

Depending on packaging strategy, these may also be delivered through local download or asset caching.

## Permissions

The transcription and overlay flow uses:

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

- Audio capture should run on `Dispatchers.IO`.
- Inference should run off the main thread.
- UI state should be owned by `ViewModel`.
- Overlay behavior must respect Android background limits.
- The app should remain fully offline and avoid cloud dependencies.

## License

See [LICENSE](LICENSE).
