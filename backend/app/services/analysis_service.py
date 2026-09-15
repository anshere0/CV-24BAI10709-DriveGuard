from __future__ import annotations

import shutil
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional
from uuid import uuid4

from fastapi import UploadFile
from sqlalchemy.orm import Session

from backend.app.models.database import (
    AnalysisJob,
    AnalysisSession,
    Incident,
    ReportArtifact,
    UploadedVideo,
)
from backend.app.database.repositories import (
    AnalysisJobRepository,
    UploadedVideoRepository,
)
from backend.app.schemas.api_schemas import (
    CreateAnalysisJobRequestDto,
)
from backend.app.core.config import load_app_config


class BackendValidationError(ValueError):
    pass


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


def normalize_backend_path(path: str) -> str:
    return str(Path(path).expanduser().resolve())


def store_uploaded_video(
    session: Session,
    upload: UploadFile,
    config_path: str = "config.yaml",
    source_origin: str = "web_upload",
) -> UploadedVideo:
    app_config = load_app_config(config_path)
    uploads_directory = Path(normalize_backend_path(app_config.database.url.replace("sqlite:///", "") + "_uploads"))
    uploads_directory.mkdir(parents=True, exist_ok=True)

    safe_name = Path(upload.filename or "video.bin").name
    stored_path = uploads_directory / f"upload_{uuid4()}_{safe_name}"

    size_bytes = 0
    with stored_path.open("wb") as destination:
        while True:
            chunk = upload.file.read(1024 * 1024)
            if not chunk:
                break
            size_bytes += len(chunk)
            destination.write(chunk)

    video = UploadedVideo(
        original_filename=safe_name,
        stored_path=normalize_backend_path(str(stored_path)),
        source_origin=source_origin,
        content_type=upload.content_type,
        size_bytes=size_bytes,
    )
    return UploadedVideoRepository(session).add(video)


def create_analysis_job(session: Session, payload: CreateAnalysisJobRequestDto) -> AnalysisJob:
    normalized_source_paths = _normalize_source_paths(payload.source_paths)
    _validate_job_sources(session, normalized_source_paths, payload)
    job = AnalysisJob(
        status="queued",
        source_type=payload.source_type,
        source_origin=payload.source_origin,
        source_paths=normalized_source_paths,
        uploaded_video_ids=list(payload.uploaded_video_ids),
        config_path=payload.config_path,
    )
    return AnalysisJobRepository(session).add(job)


def _normalize_source_paths(source_paths: list[str]) -> list[str]:
    return [normalize_backend_path(source_path) for source_path in source_paths]


def _validate_job_sources(session: Session, source_paths: list[str], payload: CreateAnalysisJobRequestDto) -> None:
    for source_path in source_paths:
        if not Path(source_path).exists():
            raise BackendValidationError(f"Source path does not exist: {source_path}")
    if payload.uploaded_video_ids:
        videos = UploadedVideoRepository(session).list_by_ids(payload.uploaded_video_ids)
        if len(videos) != len(payload.uploaded_video_ids):
            raise BackendValidationError("One or more uploaded video ids do not exist.")


def resolve_job_source_paths(session: Session, job: AnalysisJob) -> list[str]:
    uploaded_videos = UploadedVideoRepository(session).list_by_ids(job.uploaded_video_ids)
    uploaded_paths = [video.stored_path for video in uploaded_videos]
    return [*job.source_paths, *uploaded_paths]


def reset_job_results(job: AnalysisJob) -> None:
    job.sessions.clear()
    job.artifacts.clear()
    job.cancel_requested = False
    job.progress_percent = 0.0
    job.progress_phase = "queued"
    job.progress_message = "Waiting to start analysis."
    job.processed_frames = 0
    job.total_frames_estimate = 0
    job.total_sources = 0
    job.total_incidents = 0
    job.average_score = 0.0
    job.batch_report_export_path = None
    job.error_message = None


def persist_session_report(
    session: Session,
    job: AnalysisJob,
    source_path: Optional[str],
    report: object,
) -> AnalysisSession:
    from cv_engine.reporting import SessionReport

    if not isinstance(report, SessionReport):
        raise TypeError("Expected SessionReport.")

    output_directory = ""
    export_json_path: Optional[str] = None
    export_csv_path: Optional[str] = None
    if report.export_json_path:
        export_json_path = copy_artifact_to_backend(
            report.export_json_path,
            config_path=job.config_path,
            subdirectory=job.id,
        )
        output_directory = str(Path(export_json_path).parent)
    elif report.export_csv_path:
        export_csv_path = copy_artifact_to_backend(
            report.export_csv_path,
            config_path=job.config_path,
            subdirectory=job.id,
        )
        output_directory = str(Path(export_csv_path).parent)
    
    if report.export_csv_path and export_csv_path is None:
        export_csv_path = copy_artifact_to_backend(
            report.export_csv_path,
            config_path=job.config_path,
            subdirectory=job.id,
        )
        if not output_directory:
            output_directory = str(Path(export_csv_path).parent)

    session_row = AnalysisSession(
        job_id=job.id,
        source_name=report.source_name,
        source_path=source_path,
        source_origin=job.source_origin,
        frame_count=report.frame_count,
        duration_seconds=report.duration_seconds,
        score=report.score_result.score,
        penalties={key: int(value) for key, value in report.score_result.penalties.items()},
        event_counts={key: int(value) for key, value in report.event_counts.items()},
        output_directory=output_directory,
        export_json_path=export_json_path,
        export_csv_path=export_csv_path,
    )
    session.add(session_row)
    session.flush()

    for incident in report.incidents:
        session.add(
            Incident(
                session_id=session_row.id,
                event_type=incident.event_type,
                event_key=incident.event_key,
                source_name=incident.source_name,
                started_at_seconds=incident.started_at_seconds,
                ended_at_seconds=incident.ended_at_seconds,
                max_severity=incident.max_severity,
                occurrences=incident.occurrences,
                last_message=incident.last_message,
            )
        )

    if export_json_path:
        session.add(
            ReportArtifact(
                job_id=job.id,
                session_id=session_row.id,
                artifact_type="session_json",
                path=export_json_path,
            )
        )
    if export_csv_path:
        session.add(
            ReportArtifact(
                job_id=job.id,
                session_id=session_row.id,
                artifact_type="session_csv",
                path=export_csv_path,
            )
        )

    session.flush()
    return session_row


def copy_artifact_to_backend(path: str, config_path: str = "config.yaml", subdirectory: Optional[str] = None) -> str:
    app_config = load_app_config(config_path)
    artifacts_directory = Path(app_config.video.output_directory)
    if subdirectory:
        artifacts_directory = artifacts_directory / subdirectory
    artifacts_directory.mkdir(parents=True, exist_ok=True)
    source = Path(normalize_backend_path(path))
    destination = artifacts_directory / source.name
    if source.exists() and source.resolve() != destination.resolve():
        shutil.copy2(source, destination)
        return normalize_backend_path(str(destination))
    return normalize_backend_path(str(source))
