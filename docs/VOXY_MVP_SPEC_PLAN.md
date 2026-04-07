# Voxy MVP Spec Plan

## 1. Goal
Build an offline Android MVP with:
- Live on-device speech transcription using a Rust backend with Parakeet
- Saved transcription notes list
- Floating overlay mic control
- Fully local processing and storage

## 2. Product Scope (Locked)
- Primary flow: user taps mic to start/stop recording.
- On stop: finalize transcription and auto-save as a note.
- Screen 1: `Transcribe` (live state + transcript preview/result).
- Screen 2: `Notes` (history list ordered newest-first).
- Overlay service included in MVP (foreground + draggable bubble).
- Note schema: `text + createdAt`.

## 3. Architecture (MVVM + Clean, single module)

### 3.1 Layers
- **Presentation**: Compose screens + ViewModels + UI state contracts.
- **Domain**: Use cases that coordinate business rules.
- **Data**: Repository implementation, Room, clipboard, and native transcription bridge.
- **System**: Foreground overlay service + permission gateways.
- **Native**: Rust backend for audio inference and Parakeet model execution.

### 3.2 Unidirectional Data Flow
- UI event -> ViewModel -> UseCase -> Repository/DataSource -> Rust backend / Room -> StateFlow update -> UI render.
- Service uses the same use cases/repository path as app screens.

### 3.3 Dependency Injection
- Koin-based module graph for MVP.

## 4. Package and File Structure
Create and keep code under:
- `app/src/main/java/com/sublime/supersherpa/core/audio/`
- `app/src/main/java/com/sublime/supersherpa/core/ai/`
- `app/src/main/java/com/sublime/supersherpa/core/overlay/`
- `app/src/main/java/com/sublime/supersherpa/core/permissions/`
- `app/src/main/java/com/sublime/supersherpa/feature/transcription/`
- `app/src/main/java/com/sublime/supersherpa/ui/floating/`
- `app/src/main/java/com/sublime/supersherpa/model/`

Suggested additions for clarity:
- `data/local/` (Room entity/dao/database)
- `data/repository/`
- `domain/usecase/`
- `di/` (Koin modules)
- `rust/` or existing Rust workspace for the native backend

## 5. Specs by Implementation Slice

## Spec 1: Dependency and Build Setup
### Deliverables
- Add version-catalog entries and dependencies for:
  - Room runtime + ktx + compiler (ksp or kapt)
  - Lifecycle ViewModel Compose
  - Coroutines Android
  - Koin Android + Compose
- Add Rust backend integration:
  - Build the native backend as a Rust library
  - Load the resulting `.so` from `jniLibs` or a Gradle-managed native output
- Confirm `minSdk=26` remains unchanged.

### Acceptance Criteria
- `./gradlew assembleDebug` succeeds with dependency graph resolved.

## Spec 2: Data Model + Room Persistence
### Deliverables
- `TranscriptionNote` domain model (id, text, createdAt).
- Room:
  - `NoteEntity`
  - `NoteDao` with `insert()` and `observeAllByCreatedAtDesc()`
  - `AppDatabase`
- Mapper(s) entity <-> domain model.
- Repository contract + implementation for notes persistence.

### Acceptance Criteria
- Unit/instrumented DB test verifies inserts and descending timestamp ordering.

## Spec 3: Voice State Contract
### Deliverables
- Sealed UI state in `model` or `feature/transcription`:
  - `Idle`
  - `Listening`
  - `Processing`
  - `Result(text: String)`
  - Optional `Error(message: String)` for resilience

### Acceptance Criteria
- ViewModel and UI consume the same single voice state contract.

## Spec 4: Audio Recorder Core
### Deliverables
- `core/audio/AudioRecorder.kt` using `AudioRecord`:
  - Config: 16_000 Hz, mono, PCM_16BIT
  - `startRecording(onAudioFrame)`
  - `stopRecording()`
  - Internal streaming loop on `Dispatchers.IO`
- Guard invalid states (double-start/double-stop).

### Acceptance Criteria
- Start/stop cycle is stable and no ANR or main-thread audio work.

## Spec 5: Rust Engine Core
### Deliverables
- `core/ai/RustEngine.kt` with API:
  - `initialize(context)`
  - `startStreaming()`
  - `acceptAudio(shortArrayOrByteArrayChunk)`
  - `getResult()` (partial/final retrieval strategy)
  - `stop()`
- Load Parakeet model assets from `assets/models/` or the app's asset delivery path:
  - model files required by the Rust backend
- Inference work off the main thread; JNI calls should be wrapped in Kotlin repository/bridge code.

### Acceptance Criteria
- Engine initializes once and can be reused across multiple sessions.
- No app crash if assets/native library are missing; surface recoverable error state.

## Spec 6: Transcription Repository + Use Cases
### Deliverables
- Repository combines recorder + Rust engine + Room.
- Use cases:
  - Start transcription stream
  - Observe partial text
  - Stop and finalize text
  - Auto-save final text as note on stop
  - Observe notes list
  - Copy transcript

### Acceptance Criteria
- On stop, non-blank final transcription is persisted automatically.

## Spec 7: Transcription ViewModel
### Deliverables
- `feature/transcription/TranscriptionViewModel.kt`:
  - exposes `StateFlow<VoiceState>`
  - exposes current transcript text (if separate)
  - handles `onMicToggle()`
  - handles copy action
- Coroutine scopes and cancellation are lifecycle-safe (`viewModelScope`).

### Acceptance Criteria
- Deterministic transitions: Idle -> Listening -> Processing -> Result -> Idle/listening cycle.

## Spec 8: Compose UI - Two Screens
### Deliverables
- Replace placeholder destinations with:
  - `Transcribe`
  - `Notes`
- `TranscribeScreen`:
  - Mic button (tap-to-toggle)
  - State indicator
  - Transcript text area
  - Copy action
- `NotesScreen`:
  - Lazy list of saved notes (`text`, formatted createdAt)
  - Empty state UI

### Acceptance Criteria
- User can record/stop, see transcript, and observe auto-saved entry in notes list.

## Spec 9: Overlay Foreground Service
### Deliverables
- `core/overlay/VoiceOverlayService.kt`:
  - Foreground service notification
  - Overlay permission checks
  - Draggable bubble using `WindowManager` + `TYPE_APPLICATION_OVERLAY`
- `ui/floating/FloatingBubble.kt` for states:
  - idle, listening, processing, result
- Service invokes the same transcription use cases as app screens.

### Acceptance Criteria
- Overlay appears, drags smoothly, start/stop works, and notes are saved.

## Spec 10: Manifest + Permissions + Android 14+
### Deliverables
- Add permissions:
  - `RECORD_AUDIO`
  - `FOREGROUND_SERVICE`
  - `SYSTEM_ALERT_WINDOW`
  - `POST_NOTIFICATIONS`
- Declare overlay service with appropriate foreground microphone type.
- Runtime permission flow in app entry and overlay setup guidance.

### Acceptance Criteria
- No immediate permission/security crash when using transcription/overlay flows.

## Spec 11: DI Wiring (Koin)
### Deliverables
- Koin modules for core/data/domain/feature/service dependencies.
- Application class initializes Koin.
- ViewModels injected cleanly.

### Acceptance Criteria
- App boot succeeds and all required dependencies resolve.

## Spec 12: Testing and Verification
### Deliverables
- Unit tests:
  - ViewModel state transitions
  - Auto-save behavior
  - Notes ordering
- Compose/UI tests:
  - Mic toggle flow on `TranscribeScreen`
  - Note appears on `NotesScreen` after stop
- Manual QA checklist run:
  - overlay appears
  - recording works
  - transcription responsive
  - copy action works
  - no obvious crashes

### Acceptance Criteria
- `./gradlew testDebugUnitTest` passes.
- Any unexecuted checks documented explicitly.

## 6. Non-Functional Constraints
- Fully offline operation.
- Model loaded once and reused where possible.
- No heavy work on main thread.
- Keep changes scoped; avoid unrelated refactors.

## 7. Risks and Mitigations
- **Rust/native binary or model mismatch**: lock model version and backend compatibility.
- **Overlay OEM behavior differences**: provide in-app fallback to Transcribe screen.
- **Permission denial**: degrade gracefully with actionable UI prompts.
- **Realtime latency**: tune streaming chunk sizes for responsiveness.

## 8. Done Definition (MVP)
MVP is complete when all are true:
- User can transcribe speech on-device from app UI.
- Final text is auto-saved and visible in notes history.
- User can trigger transcription via floating overlay.
- Core flows are stable in debug builds with no obvious crash path.
