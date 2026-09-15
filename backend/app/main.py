from __future__ import annotations

import os
from contextlib import asynccontextmanager
from typing import Iterator, Optional

from fastapi import Depends, FastAPI, File, Form, HTTPException, Query, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy.orm import Session

from backend.app.database.connection import get_database_runtime, init_database, load_backend_config
from backend.app.database.repositories import AnalysisJobRepository
from backend.app.schemas.api_schemas import (
    AnalysisJobListDto,
    AnalysisJobDto,
    AnalysisSessionListDto,
    AnalysisSessionDto,
    CreateAnalysisJobRequestDto,
    HealthDto,
    IncidentListDto,
    IncidentDto,
    ReportArtifactDto,
    UploadedVideoDto,
)
from backend.app.services.analysis_service import (
    BackendValidationError,
    create_analysis_job,
    store_uploaded_video,
)

# For running analysis locally / synchronously without a queue
from backend.app.core.jobs import run_analysis_job_sync

def _config_path() -> str:
    return os.getenv("DRIVEGUARD_CONFIG_PATH", "config.yaml")


@asynccontextmanager
async def lifespan(_: FastAPI) -> Iterator[None]:
    init_database(_config_path())
    yield


app_config = load_backend_config(_config_path())
app = FastAPI(
    title="DriveGuard Backend",
    version="1.0.0",
    description="Backend for DriveGuard AI Computer Vision Engine.",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
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
    config = load_backend_config(_config_path())
    return HealthDto(
        status="ok",
        database_url=config.database.url,
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


@app.post(
    "/analysis-jobs",
    response_model=AnalysisJobDto,
    tags=["jobs"],
    summary="Create and run an analysis job",
)
def create_analysis_job_endpoint(
    request: CreateAnalysisJobRequestDto,
    session: Session = Depends(get_db),
) -> AnalysisJobDto:
    try:
        job = create_analysis_job(session, request)
        session.commit()
        session.refresh(job)
        
        # Run synchronous analysis directly for student simplicity
        run_analysis_job_sync(job.id, request.config_path)

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
