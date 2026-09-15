from __future__ import annotations

import os
from contextlib import asynccontextmanager
from typing import Iterator, Optional

from fastapi import Depends, FastAPI, File, Form, HTTPException, Query, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy.orm import Session

from driver_monitoring.backend.database import get_database_runtime, init_database, load_backend_config
from driver_monitoring.backend.jobs import enqueue_analysis_job, request_job_cancel
from driver_monitoring.backend.repositories import AnalysisJobRepository
from driver_monitoring.backend.schemas import (
    AnalysisJobListDto,
    AnalysisJobDto,
    AnalysisSessionListDto,
    AnalysisSessionDto,
    CreateAnalysisJobRequestDto,
    DeviceDto,
    DeviceSessionDto,
    EdgeEventDto,
    HealthDto,
    IncidentListDto,
    IncidentDto,
    QueueMetadataDto,
    ReportArtifactDto,
    SessionEdgeSummaryDto,
    UpsertDeviceRequestDto,
    UpsertDeviceSessionRequestDto,
    UpsertEdgeEventRequestDto,
    UploadedVideoDto,
)
from driver_monitoring.backend.services import (
    BackendValidationError,
    build_session_edge_summary,
    create_analysis_job,
    store_uploaded_video,
    upsert_device,
    upsert_device_session,
    upsert_edge_event,
)
from driver_monitoring.contracts import AnalyzeBatchRequestDto, AnalyzeVideoRequestDto, BatchReportDto
from driver_monitoring.core import analyze_batch, analyze_video


def _config_path() -> str:
    return os.getenv("DRIVEGUARD_CONFIG_PATH", "config.toml")


@asynccontextmanager
async def lifespan(_: FastAPI) -> Iterator[None]:
    init_database(_config_path())
    yield


app_config = load_backend_config(_config_path())
app = FastAPI(
    title=app_config.backend.api_title,
    version=app_config.backend.api_version,
    description="Persistent backend for DriveGuard AI with job-based video analysis and local dev endpoints.",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=app_config.backend.cors_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


def get_db() -> Iterator[Session]:
    runtime = get_database_runtime(_config_path())
    session = runtime.session_factory()
    try:
        yield session
        session.commit()
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()


@app.get("/health", response_model=HealthDto, tags=["system"], summary="Check backend health")
def health() -> HealthDto:
    backend = load_backend_config(_config_path()).backend
    return HealthDto(
        status="ok",
        queue_backend=backend.queue_backend,
        database_url=backend.database_url,
    )


@app.post(
    "/videos",
    response_model=UploadedVideoDto,
    tags=["uploads"],
    summary="Upload a video file for later analysis",
)
def upload_video(
    file: UploadFile = File(...),
    source_origin: str = Form(default="web_upload"),
    session: Session = Depends(get_db),
) -> UploadedVideoDto:
    stored_video = store_uploaded_video(session, file, _config_path(), source_origin=source_origin)
    return UploadedVideoDto.model_validate(stored_video)


@app.put(
    "/mobile/devices/{device_id}",
    response_model=DeviceDto,
    tags=["mobile"],
    summary="Register or refresh one Android edge device",
)
def upsert_mobile_device(
    device_id: str,
    request: UpsertDeviceRequestDto,
    session: Session = Depends(get_db),
) -> DeviceDto:
    try:
        device = upsert_device(session, device_id, request)
        return DeviceDto.model_validate(device)
    except BackendValidationError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.put(
    "/mobile/device-sessions/{device_session_id}",
    response_model=DeviceSessionDto,
    tags=["mobile"],
    summary="Register or update one Android device session",
)
def upsert_mobile_device_session(
    device_session_id: str,
    request: UpsertDeviceSessionRequestDto,
    session: Session = Depends(get_db),
) -> DeviceSessionDto:
    try:
        device_session = upsert_device_session(session, device_session_id, request)
        return DeviceSessionDto.model_validate(device_session)
    except BackendValidationError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.put(
    "/mobile/edge-events/{edge_event_id}",
    response_model=EdgeEventDto,
    tags=["mobile"],
    summary="Register or update one Android edge event with local context",
)
def upsert_mobile_edge_event(
    edge_event_id: str,
    request: UpsertEdgeEventRequestDto,
    session: Session = Depends(get_db),
) -> EdgeEventDto:
    try:
        edge_event = upsert_edge_event(session, edge_event_id, request)
        return EdgeEventDto.model_validate(edge_event)
    except BackendValidationError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.post(
    "/analysis-jobs",
    response_model=AnalysisJobDto,
    tags=["jobs"],
    summary="Create and enqueue an analysis job",
)
def create_analysis_job_endpoint(
    request: CreateAnalysisJobRequestDto,
    session: Session = Depends(get_db),
) -> AnalysisJobDto:
    try:
        job = create_analysis_job(session, request)
        session.commit()
        session.refresh(job)
        queue_job_id = enqueue_analysis_job(job.id, request.config_path)
        if queue_job_id:
            job.queue_job_id = queue_job_id
            session.commit()
        session.expire_all()
        refreshed_job = AnalysisJobRepository(session).get(job.id)
        if refreshed_job is None:
            raise HTTPException(status_code=404, detail="Analysis job not found after enqueue.")
        return AnalysisJobDto.model_validate(refreshed_job)
    except BackendValidationError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.get(
    "/analysis-jobs",
    response_model=AnalysisJobListDto,
    tags=["jobs"],
    summary="List persisted analysis jobs",
)
def list_analysis_jobs(
    status: Optional[str] = Query(default=None),
    limit: int = Query(default=20, ge=1, le=200),
    session: Session = Depends(get_db),
) -> AnalysisJobListDto:
    items = AnalysisJobRepository(session).list_jobs(status=status, limit=limit)
    return AnalysisJobListDto(
        items=[AnalysisJobDto.model_validate(item) for item in items],
        total=len(items),
        status_filter=status,
        limit=limit,
    )


@app.get(
    "/analysis-jobs/{job_id}",
    response_model=AnalysisJobDto,
    tags=["jobs"],
    summary="Get a persisted analysis job by id",
)
def get_analysis_job(job_id: str, session: Session = Depends(get_db)) -> AnalysisJobDto:
    job = AnalysisJobRepository(session).get(job_id)
    if job is None:
        raise HTTPException(status_code=404, detail="Analysis job not found.")
    return AnalysisJobDto.model_validate(job)


@app.post(
    "/analysis-jobs/{job_id}/cancel",
    response_model=AnalysisJobDto,
    tags=["jobs"],
    summary="Request cancellation of an analysis job",
)
def cancel_analysis_job(job_id: str, session: Session = Depends(get_db)) -> AnalysisJobDto:
    job = AnalysisJobRepository(session).get(job_id)
    if job is None:
        raise HTTPException(status_code=404, detail="Analysis job not found.")

    request_job_cancel(job_id, _config_path())
    session.expire_all()
    refreshed_job = AnalysisJobRepository(session).get(job_id)
    if refreshed_job is None:
        raise HTTPException(status_code=404, detail="Analysis job not found after cancellation request.")
    return AnalysisJobDto.model_validate(refreshed_job)


@app.get(
    "/sessions",
    response_model=AnalysisSessionListDto,
    tags=["sessions"],
    summary="List analysis sessions",
)
def list_sessions(
    job_id: Optional[str] = Query(default=None),
    session: Session = Depends(get_db),
) -> AnalysisSessionListDto:
    sessions = AnalysisJobRepository(session).list_sessions(job_id)
    return AnalysisSessionListDto(
        items=[AnalysisSessionDto.model_validate(item) for item in sessions],
        total=len(sessions),
        job_id=job_id,
    )


@app.get(
    "/sessions/{session_id}",
    response_model=AnalysisSessionDto,
    tags=["sessions"],
    summary="Get one persisted analysis session",
)
def get_session(session_id: str, session: Session = Depends(get_db)) -> AnalysisSessionDto:
    record = AnalysisJobRepository(session).get_session(session_id)
    if record is None:
        raise HTTPException(status_code=404, detail="Analysis session not found.")
    return AnalysisSessionDto.model_validate(record)


@app.get(
    "/sessions/{session_id}/edge-summary",
    response_model=SessionEdgeSummaryDto,
    tags=["sessions"],
    summary="Get Android edge context linked to one analysis session",
)
def get_session_edge_summary(
    session_id: str,
    session: Session = Depends(get_db),
) -> SessionEdgeSummaryDto:
    record = AnalysisJobRepository(session).get_session(session_id)
    if record is None:
        raise HTTPException(status_code=404, detail="Analysis session not found.")
    return build_session_edge_summary(session, record)


@app.get(
    "/sessions/{session_id}/incidents",
    response_model=IncidentListDto,
    tags=["incidents"],
    summary="List incidents for a session",
)
def get_session_incidents(session_id: str, session: Session = Depends(get_db)) -> IncidentListDto:
    repository = AnalysisJobRepository(session)
    session_record = repository.get_session(session_id)
    if session_record is None:
        raise HTTPException(status_code=404, detail="Analysis session not found.")
    incidents = repository.get_incidents(session_id)
    return IncidentListDto(
        items=[IncidentDto.model_validate(item) for item in incidents],
        total=len(incidents),
        session_id=session_id,
    )


@app.get(
    "/reports/{artifact_id}",
    response_model=ReportArtifactDto,
    tags=["reports"],
    summary="Get metadata for a report artifact",
)
def get_report_artifact(artifact_id: str, session: Session = Depends(get_db)) -> ReportArtifactDto:
    artifact = AnalysisJobRepository(session).get_artifact(artifact_id)
    if artifact is None:
        raise HTTPException(status_code=404, detail="Report artifact not found.")
    return ReportArtifactDto.model_validate(artifact)


@app.get(
    "/queue",
    response_model=QueueMetadataDto,
    tags=["system"],
    summary="Inspect queue configuration",
)
def queue_metadata() -> QueueMetadataDto:
    backend = load_backend_config(_config_path()).backend
    return QueueMetadataDto(
        backend=backend.queue_backend,
        queue_name=backend.queue_name,
        queue_job_id=None,
    )


@app.post(
    "/dev/analyze/video",
    response_model=BatchReportDto,
    tags=["dev"],
    summary="Run immediate single-video analysis without persistence",
)
def analyze_video_endpoint(request: AnalyzeVideoRequestDto) -> BatchReportDto:
    result = analyze_video(request.video_path, request.config_path)
    return result.batch_report_dto


@app.post(
    "/dev/analyze/batch",
    response_model=BatchReportDto,
    tags=["dev"],
    summary="Run immediate batch analysis without persistence",
)
def analyze_batch_endpoint(request: AnalyzeBatchRequestDto) -> BatchReportDto:
    result = analyze_batch(request.video_paths, request.config_path)
    return result.batch_report_dto
