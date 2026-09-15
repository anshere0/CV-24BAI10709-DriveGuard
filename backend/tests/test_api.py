from __future__ import annotations

import importlib
import os
import tempfile
import textwrap
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from fastapi.testclient import TestClient

from backend.app.database.connection import get_database_runtime, init_database
from cv_engine.reporting import BatchReport, IncidentRecord, SessionReport
from cv_engine.runner import RunResult
from cv_engine.risk_engine import ScoreResult

class BackendApiTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.temp_path = Path(self.temp_dir.name)
        self.config_path = self.temp_path / "config.yaml"
        self.db_path = self.temp_path / "backend.db"
        self.outputs_path = self.temp_path / "outputs"
        self.outputs_path.mkdir(parents=True, exist_ok=True)

        self.config_path.write_text(
            textwrap.dedent(
                f"""
                models:
                  primary_model_path: "models/yolov8n.pt"
                  face_landmarker_path: "models/face_landmarker.task"

                runtime:
                  width: 720
                  height: 720
                  confidence_threshold: 0.25
                  output_directory: "{self.outputs_path.as_posix()}"

                face:
                  eye_closed_threshold: 0.23
                  yawn_threshold: 0.55
                  yaw_threshold: 0.035
                  pitch_down_threshold: 0.065

                events:
                  phone_use_threshold_seconds: 2.0
                  off_road_threshold_seconds: 2.0
                  eyes_closed_threshold_seconds: 1.5
                  yawn_threshold_seconds: 1.0

                database:
                  url: "sqlite:///{self.db_path.as_posix()}"

                backend:
                  uploads_directory: "{(self.temp_path / 'uploads').as_posix()}"
                  artifacts_directory: "{(self.temp_path / 'artifacts').as_posix()}"
                  api_title: "DriveGuard AI Backend Test"
                  api_version: "test"
                """
            ).strip(),
            encoding="utf-8",
        )

        os.environ["DRIVEGUARD_CONFIG_PATH"] = str(self.config_path)
        get_database_runtime.cache_clear()
        init_database(str(self.config_path))

        from backend.app import main as api_module

        self.api_module = importlib.reload(api_module)
        self.client = TestClient(self.api_module.app)

    def tearDown(self) -> None:
        self.client.close()
        runtime = get_database_runtime(str(self.config_path))
        runtime.engine.dispose()
        get_database_runtime.cache_clear()
        os.environ.pop("DRIVEGUARD_CONFIG_PATH", None)
        self.temp_dir.cleanup()

    def test_docs_endpoint_is_available(self) -> None:
        response = self.client.get("/docs")
        self.assertEqual(response.status_code, 200)
        self.assertIn("Swagger UI", response.text)

    def test_video_upload_persists_source_origin(self) -> None:
        response = self.client.post(
            "/videos",
            files={"file": ("demo.webm", b"fake live video", "video/webm")},
            data={"source_origin": "web_live"},
        )

        self.assertEqual(response.status_code, 200, response.text)
        payload = response.json()
        self.assertEqual(payload["source_origin"], "web_live")

    def test_job_creation_persists_completed_job_and_session(self) -> None:
        source_path = str(self.temp_path / "clip.mp4")
        Path(source_path).write_bytes(b"fake video")
        session_json = self.temp_path / "session.json"
        session_csv = self.temp_path / "session.csv"
        batch_json = self.temp_path / "batch.json"
        session_json.write_text("{}", encoding="utf-8")
        session_csv.write_text("event_type\\nPHONE_USE\\n", encoding="utf-8")
        batch_json.write_text("{}", encoding="utf-8")

        fake_report = SessionReport(
            source_name=source_path,
            frame_count=12,
            duration_seconds=4.0,
            score_result=ScoreResult(score=85, penalties={"PHONE_USE": 15}, severity_level="MODERATE"),
            incidents=[
                IncidentRecord(
                    event_type="PHONE_USE",
                    event_key="PHONE_USE:1",
                    source_name=source_path,
                    started_at_seconds=0.5,
                    ended_at_seconds=2.5,
                    max_severity=15,
                    occurrences=3,
                    last_message="phone near face",
                )
            ],
            event_counts={"PHONE_USE": 1},
            export_json_path=str(session_json),
            export_csv_path=str(session_csv),
        )
        fake_batch = BatchReport(
            output_directory=str(self.outputs_path),
            session_reports=[fake_report],
            total_sources=1,
            total_incidents=1,
            average_score=85.0,
            export_json_path=str(batch_json),
        )
        fake_run_result = RunResult(session_reports=[fake_report], batch_report=fake_batch)

        # Mock the synchronous background thread execution
        with patch("backend.app.main.run_analysis_job_sync") as mock_job_sync:
            response = self.client.post(
                "/analysis-jobs",
                json={
                    "source_type": "video",
                    "source_origin": "web_upload",
                    "source_paths": [source_path],
                    "uploaded_video_ids": [],
                    "config_path": str(self.config_path),
                },
            )

        self.assertEqual(response.status_code, 200, response.text)
        payload = response.json()
        self.assertEqual(payload["status"], "queued")
        self.assertEqual(payload["source_origin"], "web_upload")
        self.assertEqual(payload["progress_phase"], "queued")
        self.assertEqual(payload["progress_percent"], 0.0)

        with patch("backend.app.core.jobs.run_headless", return_value=fake_run_result):
            from backend.app.core.jobs import process_analysis_job

            process_analysis_job(payload["id"], str(self.config_path))

        job_response = self.client.get(f"/analysis-jobs/{payload['id']}")
        self.assertEqual(job_response.status_code, 200)
        persisted_job = job_response.json()
        self.assertEqual(persisted_job["status"], "completed")
        self.assertEqual(persisted_job["source_origin"], "web_upload")
        self.assertEqual(persisted_job["total_incidents"], 1)
        self.assertEqual(len(persisted_job["sessions"]), 1)
        self.assertEqual(persisted_job["sessions"][0]["score"], 85)
        self.assertEqual(persisted_job["sessions"][0]["source_origin"], "web_upload")
        self.assertEqual(persisted_job["progress_phase"], "completed")
        self.assertEqual(persisted_job["progress_percent"], 100.0)
        self.assertTrue(persisted_job["batch_report_export_path"].endswith("batch.json"))
        self.assertIn("artifacts", persisted_job)

        jobs_response = self.client.get("/analysis-jobs?limit=20")
        self.assertEqual(jobs_response.status_code, 200)
        jobs_payload = jobs_response.json()
        print("\n\nDEBUG JOBS PAYLOAD:")
        print(jobs_payload)
        print("\n\n")
        self.assertEqual(jobs_payload["total"], 1)
        self.assertEqual(jobs_payload["items"][0]["id"], persisted_job["id"])

        sessions_response = self.client.get("/sessions")
        self.assertEqual(sessions_response.status_code, 200)
        sessions_payload = sessions_response.json()
        self.assertEqual(sessions_payload["total"], 1)
        self.assertEqual(sessions_payload["items"][0]["source_name"], source_path)
        self.assertTrue(sessions_payload["items"][0]["export_json_path"].endswith("session.json"))
        self.assertIn(self.outputs_path.name, sessions_payload["items"][0]["export_json_path"])

        incidents_response = self.client.get(f"/sessions/{sessions_payload['items'][0]['id']}/incidents")
        self.assertEqual(incidents_response.status_code, 200)
        incidents_payload = incidents_response.json()
        self.assertEqual(incidents_payload["total"], 1)
        self.assertEqual(incidents_payload["items"][0]["event_type"], "PHONE_USE")

if __name__ == "__main__":
    unittest.main()
