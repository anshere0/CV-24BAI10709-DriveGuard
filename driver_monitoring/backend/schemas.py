from __future__ import annotations

from datetime import datetime
from typing import Any, Literal, Optional

from pydantic import BaseModel, ConfigDict, Field, model_validator


class ApiMessageDto(BaseModel):
    model_config = ConfigDict(extra="forbid")

    message: str


class UploadedVideoDto(BaseModel):
    model_config = ConfigDict(from_attributes=True, extra="forbid")

    id: str
    original_filename: str
    stored_path: str
    source_origin: str
    content_type: Optional[str]
    size_bytes: int
    created_at: datetime


class DeviceDto(BaseModel):
    model_config = ConfigDict(from_attributes=True, extra="forbid")

    id: str
    platform: str
    display_name: Optional[str]
    manufacturer: Optional[str]
    model: Optional[str]
    os_version: Optional[str]
    app_version: Optional[str]
    created_at: datetime
    updated_at: datetime
    last_seen_at: datetime


class DeviceSessionDto(BaseModel):
    model_config = ConfigDict(from_attributes=True, extra="forbid")

    id: str
    device_id: str
    source_origin: str
    backend_url: Optional[str]
    status: str
    started_at: datetime
    ended_at: Optional[datetime]
    created_at: datetime
    updated_at: datetime


class EdgeEventDto(BaseModel):
    model_config = ConfigDict(from_attributes=True, extra="forbid")

    id: str
    device_session_id: str
    uploaded_video_id: Optional[str]
    analysis_job_id: Optional[str]
    analysis_session_id: Optional[str]
    source_origin: str
    suspected_event_type: str
    suspected_event_title: Optional[str]
    local_confidence: float
    local_priority_score: int
    triggered_at: datetime
    capture_started_at: Optional[datetime]
    capture_ended_at: Optional[datetime]
    clip_file_name: Optional[str]
    edge_signals: dict[str, Any]
    server_confirmation_status: str
    server_confirmed_event_types: list[str]
    created_at: datetime
    updated_at: datetime


class UpsertDeviceRequestDto(BaseModel):
    model_config = ConfigDict(extra="forbid")

    platform: Literal["android"] = "android"
    display_name: Optional[str] = None
    manufacturer: Optional[str] = None
    model: Optional[str] = None
    os_version: Optional[str] = None
    app_version: Optional[str] = None


class UpsertDeviceSessionRequestDto(BaseModel):
    model_config = ConfigDict(extra="forbid")

    device_id: str
    source_origin: Literal["android_upload"] = "android_upload"
    backend_url: Optional[str] = None
    status: Literal["active", "stopped", "uploading", "completed"] = "active"
    started_at: datetime
    ended_at: Optional[datetime] = None


class UpsertEdgeEventRequestDto(BaseModel):
    model_config = ConfigDict(extra="forbid")

    device_session_id: str
    uploaded_video_id: Optional[str] = None
    analysis_job_id: Optional[str] = None
    analysis_session_id: Optional[str] = None
    source_origin: Literal["android_upload"] = "android_upload"
    suspected_event_type: str
    suspected_event_title: Optional[str] = None
    local_confidence: float = Field(ge=0.0, le=1.0)
    local_priority_score: int = Field(ge=0, le=100)
    triggered_at: datetime
    capture_started_at: Optional[datetime] = None
    capture_ended_at: Optional[datetime] = None
    clip_file_name: Optional[str] = None
    edge_signals: dict[str, Any] = Field(default_factory=dict)


class SessionEdgeSummaryDto(BaseModel):
    model_config = ConfigDict(extra="forbid")

    session_id: str
    source_origin: str
    has_edge_context: bool
    local_suspected_count: int
    confirmed_count: int
    not_confirmed_count: int
    pending_confirmation_count: int
    device: Optional[DeviceDto] = None
    device_session: Optional[DeviceSessionDto] = None
    edge_events: list[EdgeEventDto] = Field(default_factory=list)


class CreateAnalysisJobRequestDto(BaseModel):
    model_config = ConfigDict(extra="forbid")

    source_type: Literal["video", "batch"] = "video"
    source_origin: Literal["web_upload", "web_live", "android_upload"] = "web_upload"
    source_paths: list[str] = Field(default_factory=list)
    uploaded_video_ids: list[str] = Field(default_factory=list)
    config_path: str = "config.toml"

    @model_validator(mode="after")
    def validate_sources(self) -> "CreateAnalysisJobRequestDto":
        if not self.source_paths and not self.uploaded_video_ids:
            raise ValueError("At least one source path or uploaded video id is required.")
        if self.source_type == "video" and len(self.source_paths) + len(self.uploaded_video_ids) != 1:
            raise ValueError("Video jobs require exactly one source.")
        return self


class QueueMetadataDto(BaseModel):
    model_config = ConfigDict(extra="forbid")

    backend: str
    queue_name: str
    queue_job_id: Optional[str] = None


class ReportArtifactDto(BaseModel):
    model_config = ConfigDict(from_attributes=True, extra="forbid")

    id: str
    job_id: str
    session_id: Optional[str]
    artifact_type: str
    path: str
    created_at: datetime


class IncidentDto(BaseModel):
    model_config = ConfigDict(from_attributes=True, extra="forbid")

    id: str
    session_id: str
    event_type: str
    event_key: str
    source_name: str
    started_at_seconds: float
    ended_at_seconds: float
    max_severity: int
    occurrences: int
    last_message: str


class AnalysisSessionDto(BaseModel):
    model_config = ConfigDict(from_attributes=True, extra="forbid")

    id: str
    job_id: str
    source_name: str
    source_path: Optional[str]
    source_origin: str
    frame_count: int
    duration_seconds: float
    score: int
    penalties: dict[str, int]
    event_counts: dict[str, int]
    output_directory: str
    export_json_path: Optional[str]
    export_csv_path: Optional[str]
    created_at: datetime
    incidents: list[IncidentDto] = Field(default_factory=list)
    artifacts: list[ReportArtifactDto] = Field(default_factory=list)


class AnalysisJobDto(BaseModel):
    model_config = ConfigDict(from_attributes=True, extra="forbid")

    id: str
    status: str
    source_type: str
    source_origin: str
    source_paths: list[str]
    uploaded_video_ids: list[str]
    config_path: str
    queue_job_id: Optional[str]
    error_message: Optional[str]
    cancel_requested: bool
    progress_percent: float
    progress_phase: str
    progress_message: Optional[str]
    processed_frames: int
    total_frames_estimate: int
    estimated_remaining_seconds: Optional[float]
    total_sources: int
    total_incidents: int
    average_score: float
    batch_report_export_path: Optional[str]
    created_at: datetime
    started_at: Optional[datetime]
    completed_at: Optional[datetime]
    sessions: list[AnalysisSessionDto] = Field(default_factory=list)
    artifacts: list[ReportArtifactDto] = Field(default_factory=list)


class AnalysisJobListDto(BaseModel):
    model_config = ConfigDict(extra="forbid")

    items: list[AnalysisJobDto]
    total: int
    status_filter: Optional[str] = None
    limit: int


class AnalysisSessionListDto(BaseModel):
    model_config = ConfigDict(extra="forbid")

    items: list[AnalysisSessionDto]
    total: int
    job_id: Optional[str] = None


class IncidentListDto(BaseModel):
    model_config = ConfigDict(extra="forbid")

    items: list[IncidentDto]
    total: int
    session_id: str


class HealthDto(BaseModel):
    model_config = ConfigDict(extra="forbid")

    status: str
    queue_backend: str
    database_url: str
