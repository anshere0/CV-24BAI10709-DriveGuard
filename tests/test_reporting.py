from __future__ import annotations

import unittest

import numpy as np

from cv_engine.event_engine import Event
from cv_engine.reporting import SessionAggregator
from cv_engine.risk_engine import RiskEngine
from cv_engine.video_processor import FramePacket


class ReportingTests(unittest.TestCase):
    def test_session_aggregator_merges_repeated_event_keys(self) -> None:
        frame = np.zeros((10, 10, 3), dtype=np.uint8)
        aggregator = SessionAggregator("clip.mp4", RiskEngine())

        packet_1 = FramePacket(0, frame, "clip.mp4", 0.0, 30.0)
        packet_2 = FramePacket(1, frame, "clip.mp4", 1.0, 30.0)

        # Assuming Event takes (event_type, event_key, source_name, confidence, timestamp_seconds)
        # We need to look up Event constructor.
        # It's: event_type, event_key, source_name, confidence, timestamp_seconds
        
        e1 = Event(event_type="PHONE_USE", severity=15, message="", event_key="PHONE_USE:1")
        e2 = Event(event_type="PHONE_USE", severity=15, message="", event_key="PHONE_USE:1")
        
        aggregator.consume(packet_1, [e1])
        aggregator.consume(packet_2, [e2])
        report = aggregator.finalize(1.0)

        self.assertEqual(len(report.incidents), 1)
        self.assertEqual(report.incidents[0].occurrences, 2)
        # Initial is 100, Phone penalty is 15. Occurrences is 2. Penalty = 30. Score should be 70.
        self.assertEqual(report.score_result.score, 70)

    def test_session_aggregator_closes_missing_events(self) -> None:
        frame = np.zeros((10, 10, 3), dtype=np.uint8)
        aggregator = SessionAggregator("clip.mp4", RiskEngine())

        packet_1 = FramePacket(0, frame, "clip.mp4", 0.0, 30.0)
        packet_2 = FramePacket(1, frame, "clip.mp4", 1.0, 30.0)

        e1 = Event(event_type="DISTRACTION", severity=10, message="", event_key="DISTRACTION")

        aggregator.consume(packet_1, [e1])
        aggregator.consume(packet_2, [])
        report = aggregator.finalize(1.0)

        self.assertEqual(len(report.incidents), 1)
        self.assertEqual(report.incidents[0].event_type, "DISTRACTION")
        self.assertEqual(report.event_counts["DISTRACTION"], 1)


if __name__ == "__main__":
    unittest.main()
