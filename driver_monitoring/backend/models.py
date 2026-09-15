from __future__ import annotations

from datetime import datetime, timezone
from typing import Any, Optional
from uuid import uuid4

from sqlalchemy import Boolean, DateTime, Float, ForeignKey, Integer, String, Text
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship
from sqlalchemy.types import JSON


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


def new_id() -> str:
    return str(uuid4())


class Base(DeclarativeBase):
    pass


class UploadedVideo(Base):
    __tablename__ = "uploaded_videos"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=new_id)
    original_filename: Mapped[str] = mapped_column(String(255))
    stored_path: Mapped[str] = mapped_column(Text)
    source_origin: Mapped[str] = mapped_column(String(32), default="web_upload")
    content_type: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    size_bytes: Mapped[int] = mapped_column(Integer, default=0)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)

    edge_events: Mapped[list["EdgeEvent"]] = relationship(back_populates="uploaded_video")


class AnalysisJob(Base):
    __tablename__ = "analysis_jobs"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=new_id)
    status: Mapped[str] = mapped_column(String(32), index=True)
    source_type: Mapped[str] = mapped_column(String(32))
    source_origin: Mapped[str] = mapped_column(String(32), default="web_upload")
    source_paths: Mapped[list[str]] = mapped_column(JSON, default=list)
    uploaded_video_ids: Mapped[list[str]] = mapped_column(JSON, default=list)
    config_path: Mapped[str] = mapped_column(Text)
    queue_job_id: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    error_message: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    cancel_requested: Mapped[bool] = mapped_column(Boolean, default=False)
    progress_percent: Mapped[float] = mapped_column(Float, default=0.0)
    progress_phase: Mapped[str] = mapped_column(String(32), default="queued")
    progress_message: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    processed_frames: Mapped[int] = mapped_column(Integer, default=0)
    total_frames_estimate: Mapped[int] = mapped_column(Integer, default=0)
    estimated_remaining_seconds: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    total_sources: Mapped[int] = mapped_column(Integer, default=0)
    total_incidents: Mapped[int] = mapped_column(Integer, default=0)
    average_score: Mapped[float] = mapped_column(Float, default=0.0)
    batch_report_export_path: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    started_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    completed_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)

    sessions: Mapped[list["AnalysisSession"]] = relationship(
        back_populates="job",
        cascade="all, delete-orphan",
    )
    artifacts: Mapped[list["ReportArtifact"]] = relationship(
        back_populates="job",
        cascade="all, delete-orphan",
    )
    edge_events: Mapped[list["EdgeEvent"]] = relationship(back_populates="analysis_job")


class AnalysisSession(Base):
    __tablename__ = "analysis_sessions"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=new_id)
    job_id: Mapped[str] = mapped_column(ForeignKey("analysis_jobs.id"), index=True)
    source_name: Mapped[str] = mapped_column(Text)
    source_path: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    source_origin: Mapped[str] = mapped_column(String(32), default="web_upload")
    frame_count: Mapped[int] = mapped_column(Integer, default=0)
    duration_seconds: Mapped[float] = mapped_column(Float, default=0.0)
    score: Mapped[int] = mapped_column(Integer, default=100)
    penalties: Mapped[dict[str, Any]] = mapped_column(JSON, default=dict)
    event_counts: Mapped[dict[str, Any]] = mapped_column(JSON, default=dict)
    output_directory: Mapped[str] = mapped_column(Text)
    export_json_path: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    export_csv_path: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)

    job: Mapped[AnalysisJob] = relationship(back_populates="sessions")
    incidents: Mapped[list["Incident"]] = relationship(
        back_populates="session",
        cascade="all, delete-orphan",
    )
    artifacts: Mapped[list["ReportArtifact"]] = relationship(back_populates="session")
    edge_events: Mapped[list["EdgeEvent"]] = relationship(back_populates="analysis_session")


class Incident(Base):
    __tablename__ = "incidents"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=new_id)
    session_id: Mapped[str] = mapped_column(ForeignKey("analysis_sessions.id"), index=True)
    event_type: Mapped[str] = mapped_column(String(64), index=True)
    event_key: Mapped[str] = mapped_column(String(128))
    source_name: Mapped[str] = mapped_column(Text)
    started_at_seconds: Mapped[float] = mapped_column(Float)
    ended_at_seconds: Mapped[float] = mapped_column(Float)
    max_severity: Mapped[int] = mapped_column(Integer)
    occurrences: Mapped[int] = mapped_column(Integer)
    last_message: Mapped[str] = mapped_column(Text)

    session: Mapped[AnalysisSession] = relationship(back_populates="incidents")


class ReportArtifact(Base):
    __tablename__ = "report_artifacts"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=new_id)
    job_id: Mapped[str] = mapped_column(ForeignKey("analysis_jobs.id"), index=True)
    session_id: Mapped[Optional[str]] = mapped_column(ForeignKey("analysis_sessions.id"), nullable=True, index=True)
    artifact_type: Mapped[str] = mapped_column(String(64))
    path: Mapped[str] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)

    job: Mapped[AnalysisJob] = relationship(back_populates="artifacts")
    session: Mapped[Optional[AnalysisSession]] = relationship(back_populates="artifacts")


class Device(Base):
    __tablename__ = "devices"

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    platform: Mapped[str] = mapped_column(String(32), default="android")
    display_name: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    manufacturer: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    model: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    os_version: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    app_version: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow, onupdate=utcnow)
    last_seen_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)

    device_sessions: Mapped[list["DeviceSession"]] = relationship(
        back_populates="device",
        cascade="all, delete-orphan",
    )


class DeviceSession(Base):
    __tablename__ = "device_sessions"

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    device_id: Mapped[str] = mapped_column(ForeignKey("devices.id"), index=True)
    source_origin: Mapped[str] = mapped_column(String(32), default="android_upload")
    backend_url: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    status: Mapped[str] = mapped_column(String(32), index=True)
    started_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    ended_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow, onupdate=utcnow)

    device: Mapped[Device] = relationship(back_populates="device_sessions")
    edge_events: Mapped[list["EdgeEvent"]] = relationship(
        back_populates="device_session",
        cascade="all, delete-orphan",
    )


class EdgeEvent(Base):
    __tablename__ = "edge_events"

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    device_session_id: Mapped[str] = mapped_column(ForeignKey("device_sessions.id"), index=True)
    uploaded_video_id: Mapped[Optional[str]] = mapped_column(ForeignKey("uploaded_videos.id"), nullable=True, index=True)
    analysis_job_id: Mapped[Optional[str]] = mapped_column(ForeignKey("analysis_jobs.id"), nullable=True, index=True)
    analysis_session_id: Mapped[Optional[str]] = mapped_column(
        ForeignKey("analysis_sessions.id"),
        nullable=True,
        index=True,
    )
    source_origin: Mapped[str] = mapped_column(String(32), default="android_upload")
    suspected_event_type: Mapped[str] = mapped_column(String(64), index=True)
    suspected_event_title: Mapped[Optional[str]] = mapped_column(String(128), nullable=True)
    local_confidence: Mapped[float] = mapped_column(Float, default=0.0)
    local_priority_score: Mapped[int] = mapped_column(Integer, default=0)
    triggered_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    capture_started_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    capture_ended_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    clip_file_name: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    edge_signals: Mapped[dict[str, Any]] = mapped_column(JSON, default=dict)
    server_confirmation_status: Mapped[str] = mapped_column(String(32), default="pending")
    server_confirmed_event_types: Mapped[list[str]] = mapped_column(JSON, default=list)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow, onupdate=utcnow)

    device_session: Mapped[DeviceSession] = relationship(back_populates="edge_events")
    uploaded_video: Mapped[Optional[UploadedVideo]] = relationship(back_populates="edge_events")
    analysis_job: Mapped[Optional[AnalysisJob]] = relationship(back_populates="edge_events")
    analysis_session: Mapped[Optional[AnalysisSession]] = relationship(back_populates="edge_events")
