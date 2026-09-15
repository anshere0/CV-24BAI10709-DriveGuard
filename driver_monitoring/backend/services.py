from __future__ import annotations

import shutil
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional
from uuid import uuid4

from fastapi import UploadFile
from sqlalchemy.orm import Session

from driver_monitoring.backend.models import (
    AnalysisJob,
    AnalysisSession,
    Device,
    DeviceSession,
    EdgeEvent,
    Incident,
    ReportArtifact,
    UploadedVideo,
)
from driver_monitoring.backend.repositories import (
    AnalysisJobRepository,
    DeviceRepository,
    DeviceSessionRepository,
    EdgeEventRepository,
    UploadedVideoRepository,
)
from driver_monitoring.backend.schemas import (
    CreateAnalysisJobRequestDto,
    DeviceDto,
    DeviceSessionDto,
    EdgeEventDto,
    SessionEdgeSummaryDto,
    UpsertDeviceRequestDto,
    UpsertDeviceSessionRequestDto,
    UpsertEdgeEventRequestDto,
)
from driver_monitoring.config import load_app_config


class BackendValidationError(ValueError):
    pass


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


def normalize_backend_path(path: str) -> str:
    return str(Path(path).expanduser().resolve())


def store_uploaded_video(
    session: Session,
    upload: UploadFile,
    config_path: str = "config.toml",
    source_origin: str = "web_upload",
) -> UploadedVideo:
    app_config = load_app_config(config_path)
    uploads_directory = Path(normalize_backend_path(app_config.backend.uploads_directory))
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


def upsert_device(
    session: Session,
    device_id: str,
    payload: UpsertDeviceRequestDto,
) -> Device:
    repository = DeviceRepository(session)
    device = repository.get(device_id)
    now = utcnow()

    if device is None:
        device = Device(
            id=device_id,
            platform=payload.platform,
            display_name=payload.display_name,
            manufacturer=payload.manufacturer,
            model=payload.model,
            os_version=payload.os_version,
            app_version=payload.app_version,
            created_at=now,
            updated_at=now,
            last_seen_at=now,
        )
        return repository.add(device)

    device.platform = payload.platform
    device.display_name = payload.display_name
    device.manufacturer = payload.manufacturer
    device.model = payload.model
    device.os_version = payload.os_version
    device.app_version = payload.app_version
    device.updated_at = now
    device.last_seen_at = now
    session.flush()
    return device


def upsert_device_session(
    session: Session,
    device_session_id: str,
    payload: UpsertDeviceSessionRequestDto,
) -> DeviceSession:
    if DeviceRepository(session).get(payload.device_id) is None:
        raise BackendValidationError(f"Device '{payload.device_id}' does not exist.")

    repository = DeviceSessionRepository(session)
    device_session = repository.get(device_session_id)
    now = utcnow()

    if device_session is None:
        device_session = DeviceSession(
            id=device_session_id,
            device_id=payload.device_id,
            source_origin=payload.source_origin,
            backend_url=payload.backend_url,
            status=payload.status,
            started_at=payload.started_at,
            ended_at=payload.ended_at,
            created_at=now,
            updated_at=now,
        )
        return repository.add(device_session)

    device_session.device_id = payload.device_id
    device_session.source_origin = payload.source_origin
    device_session.backend_url = payload.backend_url
    device_session.status = payload.status
    device_session.started_at = payload.started_at
    device_session.ended_at = payload.ended_at
    device_session.updated_at = now
    session.flush()
    return device_session


def upsert_edge_event(
    session: Session,
    edge_event_id: str,
    payload: UpsertEdgeEventRequestDto,
) -> EdgeEvent:
    if DeviceSessionRepository(session).get(payload.device_session_id) is None:
        raise BackendValidationError(f"Device session '{payload.device_session_id}' does not exist.")
    if payload.uploaded_video_id and UploadedVideoRepository(session).get(payload.uploaded_video_id) is None:
        raise BackendValidationError(f"Uploaded video '{payload.uploaded_video_id}' does not exist.")
    if payload.analysis_job_id and AnalysisJobRepository(session).get(payload.analysis_job_id) is None:
        raise BackendValidationError(f"Analysis job '{payload.analysis_job_id}' does not exist.")
    if payload.analysis_session_id and AnalysisJobRepository(session).get_session(payload.analysis_session_id) is None:
        raise BackendValidationError(f"Analysis session '{payload.analysis_session_id}' does not exist.")

    repository = EdgeEventRepository(session)
    edge_event = repository.get(edge_event_id)
    now = utcnow()

    if edge_event is None:
        edge_event = EdgeEvent(
            id=edge_event_id,
            device_session_id=payload.device_session_id,
            uploaded_video_id=payload.uploaded_video_id,
            analysis_job_id=payload.analysis_job_id,
            analysis_session_id=payload.analysis_session_id,
            source_origin=payload.source_origin,
            suspected_event_type=payload.suspected_event_type,
            suspected_event_title=payload.suspected_event_title,
            local_confidence=payload.local_confidence,
            local_priority_score=payload.local_priority_score,
            triggered_at=payload.triggered_at,
            capture_started_at=payload.capture_started_at,
            capture_ended_at=payload.capture_ended_at,
            clip_file_name=payload.clip_file_name,
            edge_signals=dict(payload.edge_signals),
            server_confirmation_status="pending",
            server_confirmed_event_types=[],
            created_at=now,
            updated_at=now,
        )
        return repository.add(edge_event)

    edge_event.device_session_id = payload.device_session_id
    edge_event.uploaded_video_id = payload.uploaded_video_id or edge_event.uploaded_video_id
    edge_event.analysis_job_id = payload.analysis_job_id or edge_event.analysis_job_id
    edge_event.analysis_session_id = payload.analysis_session_id or edge_event.analysis_session_id
    edge_event.source_origin = payload.source_origin
    edge_event.suspected_event_type = payload.suspected_event_type
    edge_event.suspected_event_title = payload.suspected_event_title
    edge_event.local_confidence = payload.local_confidence
    edge_event.local_priority_score = payload.local_priority_score
    edge_event.triggered_at = payload.triggered_at
    edge_event.capture_started_at = payload.capture_started_at
    edge_event.capture_ended_at = payload.capture_ended_at
    edge_event.clip_file_name = payload.clip_file_name
    edge_event.edge_signals = dict(payload.edge_signals)
    edge_event.updated_at = now
    session.flush()
    return edge_event


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
    job.estimated_remaining_seconds = None
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
    from driver_monitoring.reporting import SessionReport

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


def reconcile_edge_events_for_uploaded_video(
    session: Session,
    job: AnalysisJob,
    analysis_session: AnalysisSession,
    uploaded_video_id: str,
    report: object,
) -> None:
    from driver_monitoring.reporting import SessionReport

    if not isinstance(report, SessionReport):
        raise TypeError("Expected SessionReport.")

    edge_events = EdgeEventRepository(session).list_for_uploaded_video(uploaded_video_id)
    if not edge_events:
        return

    server_event_types = {incident.event_type for incident in report.incidents}

    for edge_event in edge_events:
        confirmed_types = sorted(
            server_event_types.intersection(_expected_server_event_types(edge_event.suspected_event_type))
        )
        edge_event.analysis_job_id = job.id
        edge_event.analysis_session_id = analysis_session.id
        edge_event.server_confirmed_event_types = confirmed_types
        edge_event.server_confirmation_status = "confirmed" if confirmed_types else "not_confirmed"
        edge_event.updated_at = utcnow()

    session.flush()


def build_session_edge_summary(
    session: Session,
    analysis_session: AnalysisSession,
) -> SessionEdgeSummaryDto:
    if analysis_session.source_origin != "android_upload":
        return SessionEdgeSummaryDto(
            session_id=analysis_session.id,
            source_origin=analysis_session.source_origin,
            has_edge_context=False,
            local_suspected_count=0,
            confirmed_count=0,
            not_confirmed_count=0,
            pending_confirmation_count=0,
            device=None,
            device_session=None,
            edge_events=[],
        )

    edge_events = EdgeEventRepository(session).list_for_analysis_session(analysis_session.id)
    if not edge_events:
        return SessionEdgeSummaryDto(
            session_id=analysis_session.id,
            source_origin=analysis_session.source_origin,
            has_edge_context=False,
            local_suspected_count=0,
            confirmed_count=0,
            not_confirmed_count=0,
            pending_confirmation_count=0,
            device=None,
            device_session=None,
            edge_events=[],
        )

    device_session = DeviceSessionRepository(session).get(edge_events[0].device_session_id)
    device = DeviceRepository(session).get(device_session.device_id) if device_session is not None else None
    edge_event_dtos = [EdgeEventDto.model_validate(edge_event) for edge_event in edge_events]

    return SessionEdgeSummaryDto(
        session_id=analysis_session.id,
        source_origin=analysis_session.source_origin,
        has_edge_context=True,
        local_suspected_count=len(edge_event_dtos),
        confirmed_count=sum(1 for event in edge_event_dtos if event.server_confirmation_status == "confirmed"),
        not_confirmed_count=sum(1 for event in edge_event_dtos if event.server_confirmation_status == "not_confirmed"),
        pending_confirmation_count=sum(1 for event in edge_event_dtos if event.server_confirmation_status == "pending"),
        device=DeviceDto.model_validate(device) if device is not None else None,
        device_session=DeviceSessionDto.model_validate(device_session) if device_session is not None else None,
        edge_events=edge_event_dtos,
    )


def copy_artifact_to_backend(path: str, config_path: str = "config.toml", subdirectory: Optional[str] = None) -> str:
    app_config = load_app_config(config_path)
    artifacts_directory = Path(normalize_backend_path(app_config.backend.artifacts_directory))
    if subdirectory:
        artifacts_directory = artifacts_directory / subdirectory
    artifacts_directory.mkdir(parents=True, exist_ok=True)
    source = Path(normalize_backend_path(path))
    destination = artifacts_directory / source.name
    if source.exists() and source.resolve() != destination.resolve():
        shutil.copy2(source, destination)
        return normalize_backend_path(str(destination))
    return normalize_backend_path(str(source))


def _expected_server_event_types(suspected_event_type: str) -> set[str]:
    expected_map = {
        "phone_usage_suspected": {"PHONE_USE"},
        "distraction_suspected": {"DISTRACTION"},
        "object_distraction_suspected": {"DISTRACTION", "PHONE_USE"},
        "drowsiness_suspected": {"DROWSINESS"},
        "severe_drowsiness_suspected": {"DROWSINESS"},
        "face_missing": set(),
    }
    return expected_map.get(suspected_event_type, set())
