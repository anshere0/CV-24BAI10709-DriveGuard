# DriveGuard Android

This folder contains the Android edge client for DriveGuard AI.

It is no longer just a future upload scaffold. The app now performs local driver-risk analysis on device, keeps only useful event-driven clips, and forwards them to the backend as `android_upload`.

## Current Stack

- Kotlin
- Jetpack Compose
- CameraX
- MediaPipe Tasks Vision
- LiteRT
- Room
- WorkManager
- OkHttp

## What The App Does Today

The Android app currently provides:

- a `Home` screen
- a `Drive Session` screen
- a `Backend Settings` screen
- configurable backend base URL persisted with DataStore
- CameraX preview and local recording
- MediaPipe face landmark inference on device
- LiteRT YOLOv8n mobile object detection on device
- local rule evaluation and risk fusion
- rolling pre-trigger / post-trigger useful clip extraction
- local Room persistence for:
  - drive sessions
  - risk events
  - useful clips
- background upload retry through WorkManager
- automatic backend job creation after upload

## Local Signals And Events

### Face-derived local signals

The app currently derives local driver signals such as:

- face present / missing
- eye closure
- yawn intensity
- head turn
- head pitch
- downward gaze proxy
- head nod proxy

### Local risk object detection

The current mobile YOLO path focuses on a narrow useful set of risk objects:

- phone
- bottle
- cup

These detections are exposed locally and fused with behavior signals instead of being sent raw.

### Local events

The rule engine currently emits:

- `drowsiness_suspected`
- `distraction_suspected`
- `face_missing`
- `phone_usage_suspected`
- `severe_drowsiness_suspected`
- `object_distraction_suspected`

Each event carries a local priority score that is reused to sort the upload queue and clip review surfaces.

## Useful Clip Strategy

The app does not keep arbitrary long recordings by default.

Instead it now uses a rolling local buffer:

1. record short rolling windows locally
2. keep pre-trigger context
3. wait for post-trigger context
4. finalize only clips linked to a local event
5. associate the saved clip with the local risk event in Room

This makes the Android path closer to an edge pre-triage product instead of a simple recorder.

## Backend Integration

The Android app can:

- upload finalized clips to `POST /videos`
- preserve `source_origin = android_upload`
- create analysis jobs through `POST /analysis-jobs`
- retry pending uploads automatically when connectivity returns

The current backend target is configured in the Settings screen and stored locally.

## Important Files

```text
app/src/main/java/com/driveguard/mobile/
|-- app/
|-- camera/
|-- data/
|   |-- local/
|   `-- remote/
|-- drive/session/
|   |-- risk/
|   `-- ui/
|-- home/ui/
|-- inference/
|   |-- face/
|   `-- objects/
|-- settings/
`-- workers/
```

Key implementation points:

- `camera/DriveSessionCameraController.kt`
  - CameraX recording and file output control
- `inference/face/FaceLandmarkerAnalyzer.kt`
  - face signal extraction and object detection orchestration
- `inference/objects/RiskObjectDetector.kt`
  - LiteRT YOLOv8n preprocessing and postprocessing
- `drive/session/risk/RiskRuleEngine.kt`
  - local rule engine, fusion rules, suspicion score, priority
- `drive/session/ui/DriveSessionRoute.kt`
  - main drive experience, useful clips, upload state, local alerts
- `data/local/*`
  - Room entities, DAO, and repository
- `workers/PendingClipUploadWorker.kt`
  - retry and backend job creation

## Assets

The app currently ships with:

- `app/src/main/assets/face_landmarker.task`
- `app/src/main/assets/yolov8n_float16.tflite`

## Local Build

Build a debug APK with:

```powershell
cd apps/driveguard-android
.\gradlew.bat app:assembleDebug
```

You can also open the folder directly in Android Studio.

## Suggested Local Test Flow

1. start the FastAPI backend from the repo root
2. install the Android app on an emulator or device
3. open `Settings` and set the backend URL
4. open `Drive Session`
5. grant camera and microphone permissions
6. start a local drive session
7. watch local alerts, useful clips, and upload queue status

For the Android emulator, the default backend URL is:

```text
http://10.0.2.2:8000
```

For a physical device, use your machine LAN IP instead.

## Current Limits

- the object detector is intentionally small and currently limited to a narrow set of risky classes
- gaze is approximated from head pose, not from a dedicated gaze model
- the current app focuses on the drive-session-first MVP and does not yet expose a polished gallery/manual upload flow
- local persistence is optimized for fast iteration and MVP experimentation, not for long-term production migration strategy

## Near-Term Direction

The current Android base is now ready for the next product-oriented steps:

- better calibration on real cabin footage
- stronger prioritization and traceability of local events
- richer Android-side review of queued clips and backend jobs
- tighter integration with the broader fleet demo flow
