from __future__ import annotations

from datetime import datetime, timezone
from pathlib import Path
from threading import Event, Lock, Thread
from time import monotonic
from typing import Optional

import cv2
from sqlalchemy.orm import Session

from backend.app.database.connection import load_backend_config, session_scope
from backend.app.models.database import AnalysisJob, ReportArtifact
from backend.app.database.repositories import AnalysisJobRepository, UploadedVideoRepository
from backend.app.services.analysis_service import (
    copy_artifact_to_backend,
    persist_session_report,
    reset_job_results,
    resolve_job_source_paths,
)
from cv_engine.pipeline import PipelineConfig
from cv_engine.runner import FrameAnalysis, run_headless
from cv_engine.reporting import SessionReport

_JOB_STOP_EVENTS: dict[str, Event] = {}
_JOB_STOP_EVENTS_LOCK = Lock()


def run_analysis_job_sync(job_id: str, config_path: str = "config.yaml") -> None:
    stop_event = _register_stop_event(job_id)
    thread = Thread(target=process_analysis_job, args=(job_id, config_path, stop_event), daemon=True)
    thread.start()


def process_analysis_job(job_id: str, config_path: str = "config.yaml", stop_event: Optional[Event] = None) -> None:
    stop_event = stop_event or _register_stop_event(job_id)
    with session_scope(config_path) as session:
        repository = AnalysisJobRepository(session)
        job = repository.get(job_id)
        if job is None:
            raise ValueError(f"Analysis job '{job_id}' does not exist.")

        if job.status == "canceled" or job.cancel_requested:
            _mark_job_canceled(job, "Analysis canceled before processing started.")
            session.commit()
            _clear_stop_event(job_id)
            return

        reset_job_results(job)
        job.status = "processing"
        job.started_at = datetime.now(timezone.utc)
        job.progress_phase = "preparing"
        job.progress_message = "Preparing analysis pipeline."
        job.progress_percent = 2.0
        source_paths = resolve_job_source_paths(session, job)
        session.commit()

        try:
            source_mode = "video" if job.source_type == "video" else "batch"
            pipeline_config = PipelineConfig.from_app_config(
                load_backend_config(job.config_path),
                source_mode=source_mode,
                source=source_paths[0] if job.source_type == "video" else source_paths,
            )
            pipeline_config.config_path = job.config_path

            frame_plan = _build_frame_plan(source_paths)
            progress_tracker = _JobProgressTracker(
                session=session,
                job=job,
                config_path=config_path,
                stop_event=stop_event,
                source_paths=source_paths,
                total_frames=max(1, frame_plan.total_frames),
                frames_before_source=frame_plan.frames_before_source,
            )
            progress_tracker.mark_preparing_sources()

            run_result = run_headless(
                pipeline_config,
                on_frame=progress_tracker.on_frame,
                on_session_complete=progress_tracker.on_session_complete,
                stop_event=stop_event,
            )

            if stop_event.is_set() or _is_cancel_requested(job_id, config_path):
                _mark_job_canceled(job, "Analysis canceled by user.")
                session.commit()
                return

            progress_tracker.mark_finalizing()
            batch_report = run_result.batch_report
            job.total_sources = batch_report.total_sources
            job.total_incidents = batch_report.total_incidents
            job.average_score = batch_report.average_score
            if batch_report.export_json_path:
                copied_path = copy_artifact_to_backend(
                    batch_report.export_json_path,
                    config_path,
                    subdirectory=job.id,
                )
                job.batch_report_export_path = copied_path
                session.add(
                    ReportArtifact(
                        job_id=job.id,
                        session_id=None,
                        artifact_type="batch_json",
                        path=copied_path,
                    )
                )

            path_by_source_name = _build_source_lookup(source_paths)
            uploaded_video_by_path = {
                video.stored_path: video.id
                for video in UploadedVideoRepository(session).list_by_ids(job.uploaded_video_ids)
            }
            for report in batch_report.session_reports:
                source_path = path_by_source_name.get(report.source_name)
                persisted_session = persist_session_report(
                    session,
                    job,
                    source_path,
                    report,
                )

            job.status = "completed"
            job.progress_phase = "completed"
            job.progress_percent = 100.0
            job.progress_message = "Analysis complete."
            job.completed_at = datetime.now(timezone.utc)
            job.estimated_remaining_seconds = 0.0
            session.commit()
        except JobCanceledError:
            _mark_job_canceled(job, "Analysis canceled by user.")
            session.commit()
        except Exception as exc:
            job.status = "failed"
            job.progress_phase = "failed"
            job.progress_message = str(exc)
            job.completed_at = datetime.now(timezone.utc)
            job.error_message = str(exc)
            job.estimated_remaining_seconds = None
            session.commit()
            raise
        finally:
            _clear_stop_event(job_id)


def _build_source_lookup(source_paths: list[str]) -> dict[str, str]:
    lookup: dict[str, str] = {}
    for source_path in source_paths:
        lookup[source_path] = source_path
    return lookup


class _FramePlan:
    def __init__(self, total_frames: int, frames_before_source: dict[str, int]) -> None:
        self.total_frames = total_frames
        self.frames_before_source = frames_before_source


def _build_frame_plan(source_paths: list[str]) -> _FramePlan:
    total_frames = 0
    frames_before_source: dict[str, int] = {}
    for source_path in source_paths:
        frames_before_source[source_path] = total_frames
        total_frames += _estimate_frame_count(source_path)
    return _FramePlan(total_frames=total_frames, frames_before_source=frames_before_source)


def _estimate_frame_count(source_path: str) -> int:
    capture = cv2.VideoCapture(source_path)
    try:
        frame_count = int(capture.get(cv2.CAP_PROP_FRAME_COUNT) or 0)
        return max(1, frame_count)
    finally:
        capture.release()


class _JobProgressTracker:
    def __init__(
        self,
        session: Session,
        job: AnalysisJob,
        config_path: str,
        stop_event: Event,
        source_paths: list[str],
        total_frames: int,
        frames_before_source: dict[str, int],
    ) -> None:
        self.session = session
        self.job = job
        self.config_path = config_path
        self.stop_event = stop_event
        self.source_paths = source_paths
        self.total_frames = max(1, total_frames)
        self.frames_before_source = frames_before_source
        self._last_committed_percent = -1.0
        self._last_source_name: Optional[str] = None
        self._completed_sources = 0
        self._started_monotonic = monotonic()

    def mark_preparing_sources(self) -> None:
        total_sources = len(self.source_paths)
        self.job.progress_phase = "processing"
        self.job.progress_percent = 5.0
        self.job.progress_message = f"Starting analysis for {total_sources} source(s)."
        self.job.total_frames_estimate = self.total_frames
        self.job.processed_frames = 0
        self.job.estimated_remaining_seconds = None
        self.session.commit()

    def mark_finalizing(self) -> None:
        self.job.progress_phase = "finalizing"
        self.job.progress_percent = 97.0
        self.job.progress_message = "Finalizing reports and persisting results."
        self.session.commit()

    def on_frame(self, analysis: FrameAnalysis) -> None:
        if self.stop_event.is_set() or self._is_cancel_requested():
            self.stop_event.set()
            raise JobCanceledError()

        source_name = analysis.packet.source_name
        processed_frames = self.frames_before_source.get(source_name, 0) + analysis.packet.frame_index + 1
        processing_ratio = min(1.0, processed_frames / self.total_frames)
        percent = 5.0 + (processing_ratio * 90.0)
        source_label = Path(source_name).name
        eta_seconds = self._estimate_remaining_seconds(processed_frames)
        eta_label = _format_eta_label(eta_seconds)
        progress_message = (
            f"Processing {source_label}: frame {analysis.packet.frame_index + 1}"
            f" ({processed_frames}/{self.total_frames})"
            f"{eta_label}"
        )

        should_commit = False
        if source_name != self._last_source_name:
            should_commit = True
        if percent - self._last_committed_percent >= 1.0:
            should_commit = True
        if analysis.packet.frame_index == 0:
            should_commit = True

        self.job.progress_phase = "processing"
        self.job.progress_percent = round(percent, 1)
        self.job.progress_message = progress_message
        self.job.processed_frames = processed_frames
        self.job.total_frames_estimate = self.total_frames
        self.job.estimated_remaining_seconds = eta_seconds

        if should_commit:
            self.session.commit()
            self._last_source_name = source_name
            self._last_committed_percent = percent

    def on_session_complete(self, session_report: SessionReport) -> None:
        self._completed_sources += 1
        total_sources = len(self.source_paths)
        self.job.progress_phase = "processing"
        self.job.progress_percent = max(self.job.progress_percent, 95.0)
        self.job.progress_message = (
            f"Completed {self._completed_sources}/{total_sources} source(s): {Path(session_report.source_name).name}"
        )
        self.session.commit()

    def _estimate_remaining_seconds(self, processed_frames: int) -> Optional[float]:
        if processed_frames <= 0:
            return None

        elapsed_seconds = monotonic() - self._started_monotonic
        if elapsed_seconds <= 0:
            return None

        remaining_frames = max(0, self.total_frames - processed_frames)
        if remaining_frames == 0:
            return 0.0

        seconds_per_frame = elapsed_seconds / processed_frames
        return round(remaining_frames * seconds_per_frame, 1)

    def _is_cancel_requested(self) -> bool:
        return _is_cancel_requested(self.job.id, self.config_path)


class JobCanceledError(RuntimeError):
    pass


def _mark_job_canceled(job: AnalysisJob, message: str) -> None:
    job.status = "canceled"
    job.progress_phase = "canceled"
    job.progress_message = message
    job.completed_at = datetime.now(timezone.utc)
    job.estimated_remaining_seconds = None


def _register_stop_event(job_id: str) -> Event:
    with _JOB_STOP_EVENTS_LOCK:
        stop_event = _JOB_STOP_EVENTS.get(job_id)
        if stop_event is None:
            stop_event = Event()
            _JOB_STOP_EVENTS[job_id] = stop_event
        return stop_event


def _get_stop_event(job_id: str) -> Optional[Event]:
    with _JOB_STOP_EVENTS_LOCK:
        return _JOB_STOP_EVENTS.get(job_id)


def _clear_stop_event(job_id: str) -> None:
    with _JOB_STOP_EVENTS_LOCK:
        _JOB_STOP_EVENTS.pop(job_id, None)


def _is_cancel_requested(job_id: str, config_path: str) -> bool:
    with session_scope(config_path) as session:
        repository = AnalysisJobRepository(session)
        job = repository.get(job_id)
        if job is None:
            return False
        return bool(job.cancel_requested or job.status == "canceled")


def _format_eta_label(eta_seconds: Optional[float]) -> str:
    if eta_seconds is None:
        return ""
    if eta_seconds < 60:
        return f" • ETA {int(round(eta_seconds))}s"
    minutes, seconds = divmod(int(round(eta_seconds)), 60)
    return f" • ETA {minutes:02d}:{seconds:02d}"
