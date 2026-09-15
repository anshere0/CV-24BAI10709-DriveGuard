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

from driver_monitoring.backend.database import get_database_runtime, init_database
from driver_monitoring.reporting import BatchReport, IncidentRecord, SessionReport
from driver_monitoring.runner import RunResult
from driver_monitoring.scoring import ScoreResult


class BackendApiTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.temp_path = Path(self.temp_dir.name)
        self.config_path = self.temp_path / "config.toml"
        self.db_path = self.temp_path / "backend.db"
        self.outputs_path = self.temp_path / "outputs"
        self.outputs_path.mkdir(parents=True, exist_ok=True)

        self.config_path.write_text(
            textwrap.dedent(
                f"""
                [models]
                primary_model_path = "yolo-Weights/yolov8n.pt"
                seatbelt_model_path = "driver_monitoring/assets/seatbelt_best.pt"
                face_landmarker_path = "driver_monitoring/assets/face_landmarker.task"

                [runtime]
                width = 720
                height = 720
                confidence_threshold = 0.25
                output_directory = "{self.outputs_path.as_posix()}"

                [face]
                eye_closed_threshold = 0.23
                yawn_threshold = 0.55
                yaw_threshold = 0.035
                pitch_down_threshold = 0.065

                [events]
                phone_use_threshold_seconds = 2.0
                off_road_threshold_seconds = 2.0
                eyes_closed_threshold_seconds = 1.5
                yawn_threshold_seconds = 1.0
                seatbelt_incorrect_threshold_seconds = 2.0
                seatbelt_missing_threshold_seconds = 3.0

                [backend]
                database_url = "sqlite:///{self.db_path.as_posix()}"
                redis_url = "redis://localhost:6379/0"
                queue_backend = "inline"
                queue_name = "driveguard-ai-test"
                uploads_directory = "{(self.temp_path / 'uploads').as_posix()}"
                artifacts_directory = "{(self.temp_path / 'artifacts').as_posix()}"
                api_title = "DriveGuard AI Backend Test"
                api_version = "test"
                """
            ).strip(),
            encoding="utf-8",
        )

        os.environ["DRIVEGUARD_CONFIG_PATH"] = str(self.config_path)
        get_database_runtime.cache_clear()
        init_database(str(self.config_path))

        from driver_monitoring import api as api_module

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
        session_csv.write_text("event_type\nPHONE_USE\n", encoding="utf-8")
        batch_json.write_text("{}", encoding="utf-8")

        fake_report = SessionReport(
            source_name=source_path,
            frame_count=12,
            duration_seconds=4.0,
            score_result=ScoreResult(score=85, penalties={"PHONE_USE": 15}),
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
        fake_core_result = SimpleNamespace(run_result=fake_run_result)

        with patch("driver_monitoring.api.enqueue_analysis_job", return_value="inline:test-job"):
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
        self.assertEqual(payload["queue_job_id"], "inline:test-job")
        self.assertEqual(payload["progress_phase"], "queued")
        self.assertEqual(payload["progress_percent"], 0.0)

        with patch("driver_monitoring.backend.jobs.run_headless", return_value=fake_run_result):
            from driver_monitoring.backend.jobs import process_analysis_job

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
        self.assertEqual(jobs_payload["total"], 1)
        self.assertEqual(jobs_payload["items"][0]["id"], persisted_job["id"])

        sessions_response = self.client.get("/sessions")
        self.assertEqual(sessions_response.status_code, 200)
        sessions_payload = sessions_response.json()
        self.assertEqual(sessions_payload["total"], 1)
        self.assertEqual(sessions_payload["items"][0]["source_name"], source_path)
        self.assertTrue(sessions_payload["items"][0]["export_json_path"].endswith("session.json"))
        self.assertIn(self.temp_path.joinpath("artifacts").name, sessions_payload["items"][0]["export_json_path"])

        incidents_response = self.client.get(f"/sessions/{sessions_payload['items'][0]['id']}/incidents")
        self.assertEqual(incidents_response.status_code, 200)
        incidents_payload = incidents_response.json()
        self.assertEqual(incidents_payload["total"], 1)
        self.assertEqual(incidents_payload["items"][0]["event_type"], "PHONE_USE")

    def test_cancel_endpoint_cancels_queued_job(self) -> None:
        source_path = str(self.temp_path / "queued_clip.mp4")
        Path(source_path).write_bytes(b"fake video")

        with patch("driver_monitoring.api.enqueue_analysis_job", return_value="inline:test-job"):
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

        cancel_response = self.client.post(f"/analysis-jobs/{payload['id']}/cancel")
        self.assertEqual(cancel_response.status_code, 200, cancel_response.text)
        cancel_payload = cancel_response.json()
        self.assertEqual(cancel_payload["status"], "canceled")
        self.assertEqual(cancel_payload["progress_phase"], "canceled")
        self.assertTrue(cancel_payload["cancel_requested"])

    def test_android_edge_context_is_persisted_and_visible_in_session_summary(self) -> None:
        device_response = self.client.put(
            "/mobile/devices/device-android-1",
            json={
                "platform": "android",
                "display_name": "Pixel Cabin Demo",
                "manufacturer": "Google",
                "model": "Pixel 8",
                "os_version": "Android 15",
                "app_version": "0.1.0",
            },
        )
        self.assertEqual(device_response.status_code, 200, device_response.text)

        session_start = "2026-03-30T01:30:00Z"
        session_end = "2026-03-30T01:36:00Z"
        device_session_response = self.client.put(
            "/mobile/device-sessions/device-session-1",
            json={
                "device_id": "device-android-1",
                "source_origin": "android_upload",
                "backend_url": "http://10.0.2.2:8000",
                "status": "stopped",
                "started_at": session_start,
                "ended_at": session_end,
            },
        )
        self.assertEqual(device_session_response.status_code, 200, device_session_response.text)

        upload_response = self.client.post(
            "/videos",
            files={"file": ("android_clip.mp4", b"fake android clip", "video/mp4")},
            data={"source_origin": "android_upload"},
        )
        self.assertEqual(upload_response.status_code, 200, upload_response.text)
        uploaded_video = upload_response.json()

        edge_event_response = self.client.put(
            "/mobile/edge-events/edge-event-1",
            json={
                "device_session_id": "device-session-1",
                "uploaded_video_id": uploaded_video["id"],
                "source_origin": "android_upload",
                "suspected_event_type": "phone_usage_suspected",
                "suspected_event_title": "Phone usage suspected",
                "local_confidence": 0.82,
                "local_priority_score": 82,
                "triggered_at": "2026-03-30T01:35:10Z",
                "capture_started_at": "2026-03-30T01:35:04Z",
                "capture_ended_at": "2026-03-30T01:35:18Z",
                "clip_file_name": "driveguard_clip_20260330_013510.mp4",
                "edge_signals": {
                    "gaze_down": True,
                    "phone_detected": True,
                    "eye_closure_percent": 41,
                    "head_pitch_percent": 29,
                },
            },
        )
        self.assertEqual(edge_event_response.status_code, 200, edge_event_response.text)

        session_json = self.temp_path / "android_session.json"
        session_csv = self.temp_path / "android_session.csv"
        batch_json = self.temp_path / "android_batch.json"
        session_json.write_text("{}", encoding="utf-8")
        session_csv.write_text("event_type\nPHONE_USE\n", encoding="utf-8")
        batch_json.write_text("{}", encoding="utf-8")

        fake_report = SessionReport(
            source_name=uploaded_video["stored_path"],
            frame_count=18,
            duration_seconds=6.5,
            score_result=ScoreResult(score=76, penalties={"PHONE_USE": 24}),
            incidents=[
                IncidentRecord(
                    event_type="PHONE_USE",
                    event_key="PHONE_USE:android:1",
                    source_name=uploaded_video["stored_path"],
                    started_at_seconds=1.0,
                    ended_at_seconds=3.4,
                    max_severity=24,
                    occurrences=2,
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
            average_score=76.0,
            export_json_path=str(batch_json),
        )
        fake_run_result = RunResult(session_reports=[fake_report], batch_report=fake_batch)

        with patch("driver_monitoring.api.enqueue_analysis_job", return_value="inline:android-job"):
            job_response = self.client.post(
                "/analysis-jobs",
                json={
                    "source_type": "video",
                    "source_origin": "android_upload",
                    "source_paths": [],
                    "uploaded_video_ids": [uploaded_video["id"]],
                    "config_path": str(self.config_path),
                },
            )

        self.assertEqual(job_response.status_code, 200, job_response.text)
        job_payload = job_response.json()

        with patch("driver_monitoring.backend.jobs.run_headless", return_value=fake_run_result):
            from driver_monitoring.backend.jobs import process_analysis_job

            process_analysis_job(job_payload["id"], str(self.config_path))

        persisted_job = self.client.get(f"/analysis-jobs/{job_payload['id']}").json()
        session_id = persisted_job["sessions"][0]["id"]

        edge_summary_response = self.client.get(f"/sessions/{session_id}/edge-summary")
        self.assertEqual(edge_summary_response.status_code, 200, edge_summary_response.text)
        edge_summary = edge_summary_response.json()
        self.assertTrue(edge_summary["has_edge_context"])
        self.assertEqual(edge_summary["source_origin"], "android_upload")
        self.assertEqual(edge_summary["local_suspected_count"], 1)
        self.assertEqual(edge_summary["confirmed_count"], 1)
        self.assertEqual(edge_summary["pending_confirmation_count"], 0)
        self.assertEqual(edge_summary["device"]["id"], "device-android-1")
        self.assertEqual(edge_summary["device_session"]["id"], "device-session-1")
        self.assertEqual(edge_summary["edge_events"][0]["suspected_event_type"], "phone_usage_suspected")
        self.assertEqual(edge_summary["edge_events"][0]["server_confirmation_status"], "confirmed")
        self.assertIn("PHONE_USE", edge_summary["edge_events"][0]["server_confirmed_event_types"])


if __name__ == "__main__":
    unittest.main()
