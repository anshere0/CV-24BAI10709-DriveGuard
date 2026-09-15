# College Requirements Checklist

This document verifies that the DriveGuard Computer Vision project meets all academic requirements for the final course submission.

## 1. Academic Scope & Originality
- [x] **Originality**: The project is a custom-built Driver Monitoring System (DMS) applying deep learning models rather than a simple tutorial clone.
- [x] **Problem Identification**: Solves the real-world problem of distracted driving, drowsiness, and cell phone usage while driving.
- [x] **Technical Solution**: Implements a complete CV pipeline that tracks faces, eyes, mouths, and objects in real-time to calculate a live risk score.
- [x] **Appropriate Scale**: The project has been scaled to a "student-sized" architecture, running locally without complex cloud or distributed microservices.

## 2. Computer Vision Concepts Applied
- [x] **Object Detection**: YOLOv8 neural network is used to detect the driver, cell phones, and contextual objects.
- [x] **Facial Landmark Tracking**: MediaPipe Face Mesh is applied to extract 468 3D facial landmarks.
- [x] **Geometric Heuristics**: Calculates Eye Aspect Ratio (EAR) for drowsiness and Mouth Aspect Ratio (MAR) for yawning.
- [x] **Head Pose Estimation**: Uses geometric projection of facial landmarks to estimate yaw, pitch, and roll to detect when the driver is looking away from the road.
- [x] **Object Tracking**: SORT/DeepSORT principles applied to track objects consistently across consecutive video frames.

## 3. Software Engineering Standards
- [x] **Modular Architecture**: Code is split cleanly into `cv_engine` (analytics), `backend` (FastAPI endpoints), and `frontend` (Vue dashboard).
- [x] **Design Patterns**: Uses Factory patterns for video sources, Observer/PubSub for event triggers, and Repository pattern for database access.
- [x] **Test-Driven**: Includes a comprehensive `pytest` suite ensuring all CV math, state transitions, and API endpoints function correctly.
- [x] **Documentation**: Features clear architecture documents, CLI help interfaces, and clean docstrings.

## 4. Demonstrability & Execution
- [x] **Executable Code**: The `main.py` CLI runs headlessly on any MP4 file or webcam feed.
- [x] **Interactive Dashboard**: A Vue 3 dashboard connects to the local FastAPI backend to display incident reports and dynamic risk charts.
- [x] **Containerization**: `docker-compose.yml` provided for single-command environment spin-up for graders.
- [x] **Offline Capability**: All models (`yolov8n.pt`, `face_landmarker.task`) are executed locally without requiring cloud API keys or external CV services.
