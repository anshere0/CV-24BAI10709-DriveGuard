from __future__ import annotations

from typing import Optional

from sqlalchemy import select
from sqlalchemy.orm import Session, joinedload

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


class UploadedVideoRepository:
    def __init__(self, session: Session) -> None:
        self.session = session

    def add(self, video: UploadedVideo) -> UploadedVideo:
        self.session.add(video)
        self.session.flush()
        return video

    def get(self, video_id: str) -> Optional[UploadedVideo]:
        return self.session.get(UploadedVideo, video_id)

    def list_by_ids(self, video_ids: list[str]) -> list[UploadedVideo]:
        if not video_ids:
            return []
        stmt = select(UploadedVideo).where(UploadedVideo.id.in_(video_ids))
        rows = list(self.session.scalars(stmt))
        rows_by_id = {row.id: row for row in rows}
        return [rows_by_id[video_id] for video_id in video_ids if video_id in rows_by_id]


class DeviceRepository:
    def __init__(self, session: Session) -> None:
        self.session = session

    def add(self, device: Device) -> Device:
        self.session.add(device)
        self.session.flush()
        return device

    def get(self, device_id: str) -> Optional[Device]:
        return self.session.get(Device, device_id)


class DeviceSessionRepository:
    def __init__(self, session: Session) -> None:
        self.session = session

    def add(self, device_session: DeviceSession) -> DeviceSession:
        self.session.add(device_session)
        self.session.flush()
        return device_session

    def get(self, session_id: str) -> Optional[DeviceSession]:
        stmt = (
            select(DeviceSession)
            .options(joinedload(DeviceSession.device), joinedload(DeviceSession.edge_events))
            .where(DeviceSession.id == session_id)
        )
        return self.session.scalars(stmt).unique().one_or_none()


class EdgeEventRepository:
    def __init__(self, session: Session) -> None:
        self.session = session

    def add(self, edge_event: EdgeEvent) -> EdgeEvent:
        self.session.add(edge_event)
        self.session.flush()
        return edge_event

    def get(self, edge_event_id: str) -> Optional[EdgeEvent]:
        stmt = select(EdgeEvent).where(EdgeEvent.id == edge_event_id)
        return self.session.scalars(stmt).one_or_none()

    def list_for_uploaded_video(self, uploaded_video_id: str) -> list[EdgeEvent]:
        stmt = (
            select(EdgeEvent)
            .where(EdgeEvent.uploaded_video_id == uploaded_video_id)
            .order_by(EdgeEvent.triggered_at.asc())
        )
        return list(self.session.scalars(stmt))

    def list_for_analysis_session(self, analysis_session_id: str) -> list[EdgeEvent]:
        stmt = (
            select(EdgeEvent)
            .where(EdgeEvent.analysis_session_id == analysis_session_id)
            .order_by(EdgeEvent.triggered_at.asc())
        )
        return list(self.session.scalars(stmt))


class AnalysisJobRepository:
    def __init__(self, session: Session) -> None:
        self.session = session

    def add(self, job: AnalysisJob) -> AnalysisJob:
        self.session.add(job)
        self.session.flush()
        return job

    def get(self, job_id: str) -> Optional[AnalysisJob]:
        stmt = (
            select(AnalysisJob)
            .options(joinedload(AnalysisJob.sessions), joinedload(AnalysisJob.artifacts))
            .where(AnalysisJob.id == job_id)
        )
        return self.session.scalars(stmt).unique().one_or_none()

    def list_jobs(self, status: Optional[str] = None, limit: int = 20) -> list[AnalysisJob]:
        safe_limit = max(1, min(limit, 200))
        stmt = (
            select(AnalysisJob)
            .options(joinedload(AnalysisJob.sessions), joinedload(AnalysisJob.artifacts))
            .order_by(AnalysisJob.created_at.desc())
            .limit(safe_limit)
        )
        if status:
            stmt = stmt.where(AnalysisJob.status == status)
        return list(self.session.scalars(stmt).unique())

    def list_sessions(self, job_id: Optional[str] = None) -> list[AnalysisSession]:
        stmt = (
            select(AnalysisSession)
            .options(joinedload(AnalysisSession.incidents), joinedload(AnalysisSession.artifacts))
            .order_by(AnalysisSession.created_at.desc())
        )
        if job_id:
            stmt = stmt.where(AnalysisSession.job_id == job_id)
        return list(self.session.scalars(stmt).unique())

    def get_session(self, session_id: str) -> Optional[AnalysisSession]:
        stmt = (
            select(AnalysisSession)
            .options(joinedload(AnalysisSession.incidents), joinedload(AnalysisSession.artifacts))
            .where(AnalysisSession.id == session_id)
        )
        return self.session.scalars(stmt).unique().one_or_none()

    def get_incidents(self, session_id: str) -> list[Incident]:
        stmt = select(Incident).where(Incident.session_id == session_id).order_by(Incident.started_at_seconds.asc())
        return list(self.session.scalars(stmt))

    def get_artifact(self, artifact_id: str) -> Optional[ReportArtifact]:
        return self.session.get(ReportArtifact, artifact_id)
