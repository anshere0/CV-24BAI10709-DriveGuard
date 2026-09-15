# DriveGuard Student Edition - College Report Material

## Chapter 1: Introduction
Driver monitoring systems (DMS) are increasingly critical for improving road safety. DriveGuard provides a non-intrusive, software-based approach to driver monitoring using off-the-shelf camera hardware. By leveraging advanced Computer Vision algorithms and deep learning models, this project demonstrates a feasible prototype for detecting drowsiness, distraction, and phone usage. 

## Chapter 2: Problem Statement & Motivation
According to recent traffic safety reports, driver fatigue and visual distraction are leading causes of severe road accidents. Proprietary embedded systems exist, but they remain cost-prohibitive for many use cases. An accessible software-only system can democratize fleet safety monitoring and provide a strong foundation for future research in human-computer interaction within vehicular environments.

## Chapter 3: Related Work
Current solutions rely heavily on embedded infrared sensors and proprietary CAN bus integration. In the open-source community, various projects address isolated problems (e.g., drowsiness via EAR, or object detection via YOLO), but few provide an integrated, end-to-end full-stack pipeline that aggregates these into a unified Risk Score.

## Chapter 4: System Architecture
DriveGuard employs a modern distributed architecture:
- **CV Engine**: A Python module handling frame-by-frame analysis using YOLOv8, MediaPipe Face Mesh, and DeepSort tracking.
- **Risk Engine**: A rule-based temporal logic system that filters noise and assigns dynamic risk scores.
- **Backend API**: A FastAPI server persisting analysis results to PostgreSQL.
- **Frontend Dashboard**: A Vue.js Single Page Application for interactive data visualization.

## Chapter 5: Methodology
1. **Face and Landmark Detection**: MediaPipe Face Mesh extracts 468 3D facial landmarks.
2. **Drowsiness / Yawning**: Calculated via the Eye Aspect Ratio (EAR) and Mouth Aspect Ratio (MAR).
3. **Head Pose Estimation**: A Perspective-n-Point (PnP) algorithm computes pitch, yaw, and roll to detect visual distraction.
4. **Object Detection**: YOLOv8 detects cell phones. DeepSort associates detections across frames to track duration.

## Chapter 6: Implementation Details
The project is implemented in Python 3.10 and Node 18, orchestrated via Docker. Configuration is externalized to `config.yaml`. The risk engine uses a point-deduction system starting from 100, penalized by event severity (computed via frequency and duration thresholds).

## Chapter 7: Results & Evaluation
Unit testing was performed using Pytest. The system successfully processes live 720p webcam feeds at over 15 FPS on a standard CPU, reliably identifying simulated events of drowsiness and phone usage within configured time thresholds (e.g., 2.0 seconds consecutive).

## Chapter 8: Conclusion & Future Work
DriveGuard effectively demonstrates that complex driver monitoring can be achieved with a unified, hardware-agnostic software pipeline. Future work could involve replacing rule-based logic with a temporal neural network (like an LSTM) for improved context awareness, and optimizing the pipeline for edge devices like the Raspberry Pi.
