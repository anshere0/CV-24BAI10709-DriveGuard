# DriveGuard: Problem Statement and Scope

## Problem Statement
Driver distraction and drowsiness remain leading causes of road accidents worldwide. Current commercial driver monitoring systems (DMS) are often expensive, proprietary, and require specialized hardware, making them inaccessible for smaller fleet operators or individual researchers. There is a need for an open, accessible, and easily deployable software-based DMS that uses standard cameras to analyze driver behavior.

## Project Scope
DriveGuard is an AI-powered driver monitoring system designed to run on standard hardware (CPUs/Edge devices) using lightweight computer vision models. It provides real-time detection and analysis of critical driver states.

### Target Users
- **Fleet Managers**: Seeking an affordable way to monitor driver safety across a fleet of vehicles.
- **Automotive Researchers**: Studying driver behavior and requiring an open platform for experimentation.
- **Developers/Students**: Learning about computer vision pipelines, edge deployment, and real-time video analytics.

### High-Level Features
1. **Real-time Event Detection**: Detects Drowsiness, Yawning, Distraction, and Phone Use using YOLOv8 and MediaPipe.
2. **Risk Scoring Engine**: Calculates an aggregate safety score (0-100) based on the frequency and severity of detected events.
3. **Multi-Mode Analysis**: Supports live webcam monitoring or batch processing of pre-recorded video files.
4. **Interactive Dashboard**: A Vue.js frontend for monitoring live sessions, reviewing historical data, and visualizing risk analytics over time.
5. **Headless Execution**: A robust CLI for running automated analysis without the graphical interface.

### Out of Scope
- Direct vehicle control or intervention (e.g., automatic braking).
- Deployment on proprietary automotive hardware (e.g., embedded CAN bus integration).
- Cloud-based training of new detection models.
