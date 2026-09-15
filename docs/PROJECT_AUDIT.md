# DriveGuard — Project Audit

**Date**: 2026-09-15  
**Purpose**: Systematic audit of the original DriveGuard AI repository to plan the student-edition transformation.

---

## 1. Repository Overview

The original DriveGuard AI is a **multi-surface driver monitoring prototype** comprising:

| Surface | Technology | Location |
|---------|-----------|----------|
| Python CV Runtime | YOLOv8 + MediaPipe + OpenCV + DeepSort | `driver_monitoring/` |
| FastAPI Backend | SQLAlchemy + SQLite (default) | `driver_monitoring/api.py`, `driver_monitoring/backend/` |
| Vue 3 Web Dashboard | Vue 3 + TypeScript + Bootstrap + Vite | `apps/driveguard-web/` |
| Android Edge App | Kotlin + CameraX + MediaPipe + LiteRT | `apps/driveguard-android/` |
| Desktop Tkinter GUI | Tkinter + PIL | `driver_monitoring/gui.py` |

**Total Python files in `driver_monitoring/`**: 19 files + 7 backend files = **26 Python files**  
**Total test files**: 4 (event engine, scoring, reporting, backend API)  
**Frontend files**: 5 views, 4 components, 1 API service, 1 router, 1 composable  
**Configuration**: TOML-based (`config.toml`)  
**Database**: SQLite via SQLAlchemy (8 tables)

---

## 2. Existing CV Pipeline Analysis

### Detection Chain
```
Video/Webcam → FramePacket → CompositeDetector (YOLOv8) → Tracker (DeepSort) → FaceMonitor (MediaPipe) → EventEngine → ScoringEngine → SessionAggregator → Export (JSON/CSV)
```

### Detections Implemented

| Detection | Method | File | Status |
|-----------|--------|------|--------|
| **Drowsiness** (EAR) | MediaPipe landmarks + temporal threshold | `face_monitor.py` | ✅ Working |
| **Yawning** (MAR) | MediaPipe landmarks + temporal threshold | `face_monitor.py` | ✅ Working |
| **Distraction** (Head pose) | MediaPipe landmarks + yaw/pitch thresholds | `face_monitor.py` | ✅ Working |
| **Phone Usage** | YOLOv8 "cell phone" + face proximity + temporal | `event_engine.py` | ✅ Working |
| **Seatbelt** | Separate YOLO model (`seatbelt_best.pt`) | `detector.py`, `event_engine.py` | ✅ Working (optional model) |
| **Knife/Sharp Object** | YOLOv8 "knife" class | `event_engine.py` | ✅ Working |
| **Face Missing** | MediaPipe face detection absence | `face_monitor.py` | ✅ Implicit |

### Temporal Logic
- **Eyes closed**: starts timer → threshold (`1.5s`) → DROWSINESS event
- **Yawning**: starts timer → threshold (`1.0s`) → YAWNING event  
- **Off-road gaze**: starts timer → threshold (`2.0s`) → DISTRACTION event
- **Phone near face**: per-track timer → threshold (`2.0s`) → PHONE_USE event
- **Seatbelt**: duration-based thresholds → NO_SEATBELT / SEATBELT_INCORRECT events

### Scoring System
- Starts at 100
- Subtracts severity per event type (DROWSINESS=20, PHONE_USE=15, DISTRACTION=10, YAWNING=8)
- Score clamped at 0
- Uses incident-level scoring (max_severity per incident, not per frame)

---

## 3. Existing Backend Analysis

### Database Tables (8 total)
1. `uploaded_videos` — file upload metadata
2. `analysis_jobs` — job queue + status + progress tracking
3. `analysis_sessions` — per-video session results
4. `incidents` — detected events per session
5. `report_artifacts` — exported JSON/CSV paths
6. `devices` — Android device registration
7. `device_sessions` — Android monitoring sessions
8. `edge_events` — Android local edge events

### API Endpoints (17 total)
- `GET /health` — health check
- `POST /videos` — upload video
- `POST /analysis-jobs` — create analysis job
- `GET /analysis-jobs` — list jobs
- `GET /analysis-jobs/{id}` — get job details
- `POST /analysis-jobs/{id}/cancel` — cancel job
- `GET /sessions` — list sessions
- `GET /sessions/{id}` — get session
- `GET /sessions/{id}/incidents` — get incidents
- `GET /sessions/{id}/edge-summary` — Android edge context
- `GET /reports/{id}` — report artifacts
- `GET /queue` — queue metadata
- `PUT /mobile/devices/{id}` — register Android device
- `PUT /mobile/device-sessions/{id}` — register device session
- `PUT /mobile/edge-events/{id}` — register edge event
- `POST /dev/analyze/video` — immediate analysis (dev)
- `POST /dev/analyze/batch` — immediate batch analysis (dev)

### Backend Architecture Pattern
- **Repository pattern** (`repositories.py`)
- **Service layer** (`services.py`)
- **Job processing** (`jobs.py`) — inline thread or Redis queue
- **Pydantic schemas** (`schemas.py`)
- **SQLAlchemy models** (`models.py`)

---

## 4. Existing Frontend Analysis

### Pages (5 Vue views)
1. **HomeView** — dashboard overview, metrics, recent sessions
2. **NewAnalysisView** — file upload form
3. **LiveDemoView** — browser camera capture + upload
4. **JobsView** — job list with status tracking + polling
5. **SessionDetailView** — session details + incidents + edge summary

### Components (4)
- `MetricCard.vue` — styled stat card
- `StatusBadge.vue` — status indicator
- `SourceBadge.vue` — source origin indicator
- `FlowTimeline.vue` — timeline visualization

### Stack
- Vue 3 + TypeScript + Composition API
- Bootstrap 5
- Vite
- vue-router

---

## 5. KEEP / MODIFY / REMOVE / REPLACE / ADD Plan

### KEEP ✅

| Item | File(s) | Reason |
|------|---------|--------|
| YOLOv8 detection | `detector.py` | Core CV — well-structured |
| MediaPipe face monitor | `face_monitor.py` | Core CV — EAR/MAR/head pose all working |
| Event engine | `event_engine.py` | Temporal logic is solid |
| Scoring engine | `scoring.py` | Simple and correct |
| Tracker (DeepSort) | `tracker.py` | Object tracking works |
| Video source abstraction | `video_source.py` | Clean webcam/video/batch handling |
| Pipeline orchestration | `pipeline.py` | Ties everything together |
| Runner (headless) | `runner.py` | Headless execution loop |
| Core analysis functions | `core.py` | Clean API for CLI/backend |
| Report data structures | `reporting.py` | Session/batch aggregation |
| Result exporter | `export.py` | JSON/CSV export + overlay drawing |
| Pydantic contracts | `contracts.py` | DTO conversion |
| Config system | `config.py` + `config.toml` | TOML-based configuration |
| Backend models | `backend/models.py` | SQLAlchemy models (modify subset) |
| Backend schemas | `backend/schemas.py` | Pydantic API schemas (modify subset) |
| Backend repositories | `backend/repositories.py` | Repository pattern |
| Backend services | `backend/services.py` | Service layer (modify) |
| Backend database setup | `backend/database.py` | DB initialization |
| FastAPI app | `api.py` | API routes (modify) |
| Job processing | `backend/jobs.py` | Inline job execution |
| Test: event engine | `tests/test_event_engine.py` | Good existing tests |
| Test: scoring | `tests/test_scoring.py` | Good existing tests |
| Test: reporting | `tests/test_reporting.py` | Good existing tests |
| Test: backend API | `tests/test_backend_api.py` | Comprehensive API tests |
| Vue dashboard | `apps/driveguard-web/` | Full frontend (modify) |
| YOLOv8 model weights | `yolo-Weights/yolov8n.pt` | Pre-trained model |
| MediaPipe model | `driver_monitoring/assets/face_landmarker.task` | Face landmarker |
| `.gitignore` | `.gitignore` | Version control config |

### MODIFY ✏️

| Item | Change | Reason |
|------|--------|--------|
| `config.toml` | Change database_url to PostgreSQL, remove redis_url, add risk weights | Student requirements: PostgreSQL, configurable risk |
| `backend/models.py` | Remove Device/DeviceSession/EdgeEvent tables, add analytics table | Remove Android-specific tables, add session analytics |
| `backend/schemas.py` | Remove Android-specific schemas, add analytics schemas, simplify | Student scope |
| `api.py` | Remove Android endpoints, add analytics endpoint, rename endpoints to match spec | Match college requirements |
| `backend/services.py` | Remove Android device/session/edge functions, add analytics service | Simplify |
| `backend/repositories.py` | Remove Android repositories, add analytics repository | Simplify |
| `backend/jobs.py` | Remove Redis/RQ paths, keep only inline processing | Simplify — no Redis needed |
| `scoring.py` | Enhance with risk classification (LOW/MODERATE/HIGH/CRITICAL), 0-100 risk score, documented formula | College requirement |
| `event_engine.py` | Remove seatbelt/knife detection (optional), add cooldown deduplication | Student scope — focus on core 4 detections |
| CLI (`cli.py`) | Restructure as `main.py`, add `--help`, print session report | College CLI requirement |
| `export.py` | Add human-readable text report generation | College reporting requirement |
| Vue frontend | Add analytics page, session history page, improve dashboard, remove Android-specific UI | Match college requirements |
| `package.json` | Add chart.js for risk charts | Visualization |

### REMOVE ❌

| Item | Reason |
|------|--------|
| `apps/driveguard-android/` | Android app — out of scope for college CV project |
| `driver_monitoring/gui.py` | Tkinter desktop GUI — not needed, CLI is required |
| `driver_monitoring/api_gui.py` | Tkinter API tester — not needed |
| `yolo.py` | GUI entry point — replaced by `main.py` CLI |
| `yolo.ipynb` | Jupyter notebook — not part of student project |
| `driver_monitoring/worker.py` | Redis/RQ worker — not needed for student scope |
| `backend/models.py` → Device, DeviceSession, EdgeEvent | Android-specific tables |
| `backend/schemas.py` → Android-specific DTOs | Mobile endpoints |
| `api.py` → Android endpoints (`/mobile/*`) | Mobile API surface |
| `api.py` → `/dev/*` endpoints | Development-only endpoints |
| `media/` directory | Android screenshots — not relevant |
| `.idea/` directory | IDE config — should not be in repo |

### REPLACE 🔄

| Item | Old | New | Reason |
|------|-----|-----|--------|
| Database | SQLite | PostgreSQL (with SQLite fallback) | College requirement |
| Config format | TOML only | YAML (`config.yaml`) | More student-friendly, matches requirements |
| Entry point | `yolo.py` / `driver_monitoring/cli.py` | `main.py` | Clean CLI entry point |
| Project structure | Flat `driver_monitoring/` | `cv_engine/` + `backend/app/` | Cleaner separation per requirements |

### ADD ➕

| Item | Purpose |
|------|---------|
| `main.py` | CLI entry point with `--video`, `--webcam`, `--help` |
| `cv_engine/` directory | Reorganized CV modules |
| `cv_engine/risk_engine.py` | Enhanced risk scoring with classification |
| `backend/app/services/analytics_service.py` | Session analytics computation |
| `requirements.txt` | Python dependencies |
| `docker-compose.yml` | Docker setup (backend + frontend + PostgreSQL) |
| `.env.example` | Environment variable template |
| `statement.md` | Problem statement document |
| `README.md` (rewrite) | Comprehensive project documentation |
| `docs/report_material.md` | College report content |
| `docs/architecture/system-architecture.md` | Architecture diagram |
| `docs/uml/use-case.md` | Use case diagram |
| `docs/uml/class-diagram.md` | Class/component diagram |
| `docs/uml/sequence-diagram.md` | Sequence diagram |
| `docs/uml/component-diagram.md` | Component diagram |
| `docs/uml/er-diagram.md` | ER diagram |
| `docs/workflow/workflow.md` | Workflow diagram |
| `samples/` directory | Sample test videos |
| `outputs/` directory | Output artifacts |
| `tests/test_risk_engine.py` | Risk engine tests |
| `tests/test_cv_utils.py` | EAR/MAR calculation tests |
| `tests/conftest.py` | Pytest fixtures |
| Vue: `AnalyticsView.vue` | Session analytics page |
| Vue: `SessionHistoryView.vue` | Session history list |
| Vue: Risk chart component | Chart.js visualization |
| `LICENSE` | License file |

---

## 6. Dependency Analysis

### Python Dependencies (Current)
```
ultralytics          # YOLOv8
opencv-python        # Video processing
mediapipe            # Face landmarks
deep-sort-realtime   # Object tracking
pillow               # Image processing (GUI only)
fastapi              # API framework
uvicorn              # ASGI server
sqlalchemy           # ORM
pydantic             # Validation
python-multipart     # File uploads
httpx                # HTTP client
redis                # Redis client (REMOVE)
rq                   # Task queue (REMOVE)
numpy                # Array operations
```

### Python Dependencies (Student Version)
```
ultralytics          # YOLOv8
opencv-python        # Video processing  
mediapipe            # Face landmarks
deep-sort-realtime   # Object tracking
numpy                # Array operations
fastapi              # API framework
uvicorn              # ASGI server
sqlalchemy           # ORM
psycopg2-binary      # PostgreSQL driver
pydantic             # Validation
python-multipart     # File uploads
pyyaml               # YAML config
pytest               # Testing
httpx                # HTTP client (testing)
```

### Frontend Dependencies
```
vue@3.5             # Framework
vue-router@4.5      # Routing
bootstrap@5.3       # CSS framework
vite@6              # Build tool
typescript@5.7      # Type checking
chart.js            # Charts (ADD)
vue-chartjs         # Vue Chart.js wrapper (ADD)
```

---

## 7. Code Quality Assessment

### Strengths
- Clean Python code with type hints throughout
- Dataclass-based data models
- Good separation of concerns (detector → tracker → face → events → scoring)
- Temporal logic is properly implemented (not single-frame)
- Repository pattern in backend
- Pydantic validation on all API endpoints
- Existing test coverage for core logic
- Vue 3 Composition API with TypeScript

### Weaknesses for Student Project
- No PostgreSQL support (SQLite only)
- No `requirements.txt`
- No `main.py` CLI entry point
- No `LICENSE` file
- Config is TOML (less common for students)
- Android/mobile code adds confusion
- Tkinter GUI is unnecessary
- Redis/RQ dependencies are enterprise-level
- No Docker support
- No documentation beyond README
- No risk classification (just raw score)
- No session analytics endpoint

---

## 8. Attribution Note

The original DriveGuard AI repository provides the foundation for:
- YOLOv8 + MediaPipe detection pipeline
- Event engine with temporal logic
- DeepSort object tracking integration
- FastAPI backend architecture
- Vue 3 dashboard structure
- Scoring system concept

The student version will:
- Reorganize the project structure
- Remove Android/mobile/enterprise components
- Add PostgreSQL support
- Enhance the risk scoring system with classification and documented formula
- Add session analytics
- Add comprehensive documentation
- Add proper CLI interface
- Add Docker support
- Add additional tests
- Create all academic documentation
