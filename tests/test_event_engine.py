from __future__ import annotations

import unittest

from cv_engine.event_engine import EventEngine
from cv_engine.face_monitor import FaceState
from cv_engine.tracker import TrackedObject


def make_track(
    track_id: int,
    label: str,
    bbox: tuple[int, int, int, int],
    duration_seconds: float,
) -> TrackedObject:
    return TrackedObject(
        track_id=track_id,
        label=label,
        confidence=0.9,
        bbox=bbox,
        duration_seconds=duration_seconds,
        source_name="clip.mp4",
    )


class EventEngineTests(unittest.TestCase):
    def test_phone_use_requires_driver_association_and_threshold(self) -> None:
        engine = EventEngine()
        face_state = FaceState(driver_present=True, face_bbox=(100, 100, 180, 180))
        tracked_objects = [
            make_track(1, "person", (60, 60, 260, 320), 4.0),
            make_track(2, "cell phone", (120, 120, 160, 170), 3.0),
        ]

        events_initial = engine.evaluate(tracked_objects, face_state, 0.0)
        events_after = engine.evaluate(tracked_objects, face_state, 2.1)

        # Phone near face is instantaneous
        self.assertIn("PHONE_NEAR_FACE", [event.event_type for event in events_initial])
        # Phone use requires 2s consecutive by default
        self.assertIn("PHONE_USE", [event.event_type for event in events_after])

    def test_drowsiness_detected(self) -> None:
        engine = EventEngine()
        face_state_drowsy = FaceState(driver_present=True, face_bbox=(100, 100, 180, 180), eyes_closed=True, eyes_closed_duration_seconds=2.0)
        tracked_objects: list[TrackedObject] = []
        
        events = engine.evaluate(tracked_objects, face_state_drowsy, 2.0)
        self.assertIn("DROWSINESS", [event.event_type for event in events])

    def test_distraction_detected(self) -> None:
        engine = EventEngine()
        face_state_distracted = FaceState(driver_present=True, face_bbox=(100, 100, 180, 180), looking_off_road=True, off_road_duration_seconds=2.5)
        tracked_objects: list[TrackedObject] = []
        
        events = engine.evaluate(tracked_objects, face_state_distracted, 2.5)
        self.assertIn("DISTRACTION", [event.event_type for event in events])


if __name__ == "__main__":
    unittest.main()
