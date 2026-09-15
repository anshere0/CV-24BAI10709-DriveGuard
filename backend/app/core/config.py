import os
import yaml
from dataclasses import dataclass
from pathlib import Path
from typing import Optional


@dataclass
class ModelSettings:
    yolo_model_path: str
    face_landmarker_path: str


@dataclass
class RuntimeSettings:
    width: int
    height: int
    confidence_threshold: float
    output_directory: str


@dataclass
class DrowsinessSettings:
    ear_threshold: float
    consecutive_seconds: float


@dataclass
class YawningSettings:
    mar_threshold: float
    consecutive_seconds: float


@dataclass
class DistractionSettings:
    yaw_threshold: float
    pitch_down_threshold: float
    consecutive_seconds: float


@dataclass
class PhoneSettings:
    confidence_threshold: float
    consecutive_seconds: float


@dataclass
class RiskSettings:
    starting_score: int
    weights: dict[str, int]
    severity_thresholds: dict[str, int]


@dataclass
class DatabaseSettings:
    url: str


@dataclass
class BackendSettings:
    host: str
    port: int
    cors_origins: list[str]


@dataclass
class AppConfig:
    models: ModelSettings
    video: RuntimeSettings
    drowsiness: DrowsinessSettings
    yawning: YawningSettings
    distraction: DistractionSettings
    phone: PhoneSettings
    risk: RiskSettings
    database: DatabaseSettings
    backend: BackendSettings


def load_app_config(config_path: str = "config.yaml") -> AppConfig:
    env_config_path = os.getenv("DRIVEGUARD_CONFIG_PATH", config_path)
    path = Path(env_config_path)
    
    if not path.exists():
        raise FileNotFoundError(f"Configuration file not found: {path}")

    with path.open("r", encoding="utf-8") as f:
        payload = yaml.safe_load(f) or {}

    models_payload = payload.get("models", {})
    video_payload = payload.get("video", {})
    drowsiness_payload = payload.get("drowsiness", {})
    yawning_payload = payload.get("yawning", {})
    distraction_payload = payload.get("distraction", {})
    phone_payload = payload.get("phone", {})
    risk_payload = payload.get("risk", {})
    database_payload = payload.get("database", {})
    backend_payload = payload.get("backend", {})

    return AppConfig(
        models=ModelSettings(
            yolo_model_path=models_payload.get("yolo_model_path", "models/yolov8n.pt"),
            face_landmarker_path=models_payload.get("face_landmarker_path", "models/face_landmarker.task"),
        ),
        video=RuntimeSettings(
            width=int(video_payload.get("width", 720)),
            height=int(video_payload.get("height", 720)),
            confidence_threshold=float(video_payload.get("confidence_threshold", 0.25)),
            output_directory=str(payload.get("output", {}).get("directory", "outputs")),
        ),
        drowsiness=DrowsinessSettings(
            ear_threshold=float(drowsiness_payload.get("ear_threshold", 0.23)),
            consecutive_seconds=float(drowsiness_payload.get("consecutive_seconds", 1.5)),
        ),
        yawning=YawningSettings(
            mar_threshold=float(yawning_payload.get("mar_threshold", 0.55)),
            consecutive_seconds=float(yawning_payload.get("consecutive_seconds", 1.0)),
        ),
        distraction=DistractionSettings(
            yaw_threshold=float(distraction_payload.get("yaw_threshold", 0.035)),
            pitch_down_threshold=float(distraction_payload.get("pitch_down_threshold", 0.065)),
            consecutive_seconds=float(distraction_payload.get("consecutive_seconds", 2.0)),
        ),
        phone=PhoneSettings(
            confidence_threshold=float(phone_payload.get("confidence_threshold", 0.25)),
            consecutive_seconds=float(phone_payload.get("consecutive_seconds", 2.0)),
        ),
        risk=RiskSettings(
            starting_score=int(risk_payload.get("starting_score", 100)),
            weights=risk_payload.get("weights", {
                "DROWSINESS": 20,
                "PHONE_USE": 15,
                "DISTRACTION": 12,
                "YAWNING": 5
            }),
            severity_thresholds=risk_payload.get("severity_thresholds", {
                "LOW": 80,
                "MODERATE": 60,
                "HIGH": 40,
                "CRITICAL": 0
            })
        ),
        database=DatabaseSettings(
            url=database_payload.get("url", "sqlite:///driveguard.db"),
        ),
        backend=BackendSettings(
            host=str(backend_payload.get("host", "127.0.0.1")),
            port=int(backend_payload.get("port", 8000)),
            cors_origins=backend_payload.get("cors_origins", ["http://localhost:5173", "http://127.0.0.1:5173"]),
        ),
    )
