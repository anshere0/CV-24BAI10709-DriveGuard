from __future__ import annotations

from contextlib import contextmanager
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path
from typing import Iterator

from sqlalchemy import create_engine, inspect, text
from sqlalchemy.engine import Engine
from sqlalchemy.orm import Session, sessionmaker

from driver_monitoring.backend.models import Base
from driver_monitoring.config import AppConfig, load_app_config


@dataclass(frozen=True)
class DatabaseRuntime:
    config_path: str
    engine: Engine
    session_factory: sessionmaker[Session]


def _normalize_database_url(database_url: str) -> str:
    if database_url.startswith("sqlite:///"):
        relative_path = database_url.removeprefix("sqlite:///")
        if relative_path == ":memory:":
            return database_url
        return f"sqlite:///{Path(relative_path).resolve()}"
    return database_url


@lru_cache(maxsize=8)
def get_database_runtime(config_path: str = "config.toml") -> DatabaseRuntime:
    app_config = load_app_config(config_path)
    database_url = _normalize_database_url(app_config.backend.database_url)
    connect_args: dict[str, object] = {}
    if database_url.startswith("sqlite"):
        connect_args["check_same_thread"] = False
    engine = create_engine(database_url, future=True, connect_args=connect_args)
    session_factory = sessionmaker(bind=engine, autoflush=False, autocommit=False, expire_on_commit=False)
    return DatabaseRuntime(config_path=config_path, engine=engine, session_factory=session_factory)


def init_database(config_path: str = "config.toml") -> None:
    runtime = get_database_runtime(config_path)
    Base.metadata.create_all(runtime.engine)
    _ensure_analysis_job_progress_columns(runtime.engine)
    _ensure_source_origin_columns(runtime.engine)


def _ensure_analysis_job_progress_columns(engine: Engine) -> None:
    inspector = inspect(engine)
    if "analysis_jobs" not in inspector.get_table_names():
        return

    existing_columns = {column["name"] for column in inspector.get_columns("analysis_jobs")}
    missing_columns: list[tuple[str, str]] = []
    if "progress_percent" not in existing_columns:
        missing_columns.append(("progress_percent", "FLOAT NOT NULL DEFAULT 0.0"))
    if "progress_phase" not in existing_columns:
        missing_columns.append(("progress_phase", "VARCHAR(32) NOT NULL DEFAULT 'queued'"))
    if "progress_message" not in existing_columns:
        missing_columns.append(("progress_message", "TEXT NULL"))
    if "cancel_requested" not in existing_columns:
        missing_columns.append(("cancel_requested", "BOOLEAN NOT NULL DEFAULT 0"))
    if "processed_frames" not in existing_columns:
        missing_columns.append(("processed_frames", "INTEGER NOT NULL DEFAULT 0"))
    if "total_frames_estimate" not in existing_columns:
        missing_columns.append(("total_frames_estimate", "INTEGER NOT NULL DEFAULT 0"))
    if "estimated_remaining_seconds" not in existing_columns:
        missing_columns.append(("estimated_remaining_seconds", "FLOAT NULL"))

    if not missing_columns:
        return

    with engine.begin() as connection:
        for column_name, column_definition in missing_columns:
            connection.execute(text(f"ALTER TABLE analysis_jobs ADD COLUMN {column_name} {column_definition}"))


def _ensure_source_origin_columns(engine: Engine) -> None:
    inspector = inspect(engine)
    target_tables = ("uploaded_videos", "analysis_jobs", "analysis_sessions")
    table_columns = {
        table_name: {column["name"] for column in inspector.get_columns(table_name)}
        for table_name in target_tables
        if table_name in inspector.get_table_names()
    }

    missing_columns: list[tuple[str, str, str]] = []
    if "uploaded_videos" in table_columns and "source_origin" not in table_columns["uploaded_videos"]:
        missing_columns.append(("uploaded_videos", "source_origin", "VARCHAR(32) NOT NULL DEFAULT 'web_upload'"))
    if "analysis_jobs" in table_columns and "source_origin" not in table_columns["analysis_jobs"]:
        missing_columns.append(("analysis_jobs", "source_origin", "VARCHAR(32) NOT NULL DEFAULT 'web_upload'"))
    if "analysis_sessions" in table_columns and "source_origin" not in table_columns["analysis_sessions"]:
        missing_columns.append(("analysis_sessions", "source_origin", "VARCHAR(32) NOT NULL DEFAULT 'web_upload'"))

    if not missing_columns:
        return

    with engine.begin() as connection:
        for table_name, column_name, column_definition in missing_columns:
            connection.execute(text(f"ALTER TABLE {table_name} ADD COLUMN {column_name} {column_definition}"))


@contextmanager
def session_scope(config_path: str = "config.toml") -> Iterator[Session]:
    runtime = get_database_runtime(config_path)
    session = runtime.session_factory()
    try:
        yield session
        session.commit()
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()


def load_backend_config(config_path: str = "config.toml") -> AppConfig:
    return load_app_config(config_path)
