import unittest
from cv_engine.risk_engine import RiskEngine
from cv_engine.event_engine import Event
from cv_engine.reporting import IncidentRecord

class RiskEngineTests(unittest.TestCase):
    def test_starting_score(self):
        engine = RiskEngine()
        result = engine.calculate([])
        self.assertEqual(result.score, 100)
        self.assertEqual(result.severity_level, "LOW")

    def test_calculate_events(self):
        engine = RiskEngine()
        events = [
            Event(event_type="DROWSINESS", severity=20, message="", event_key="DROWSINESS"),
            Event(event_type="PHONE_USE", severity=15, message="", event_key="PHONE_USE")
        ]
        result = engine.calculate(events)
        
        # Base penalties: DROWSINESS (20) + PHONE_USE (15) = 35
        # Expected score: 100 - 35 = 65
        self.assertEqual(result.score, 65)
        self.assertEqual(result.severity_level, "MODERATE")

    def test_calculate_from_incidents(self):
        engine = RiskEngine()
        incidents = [
            IncidentRecord(
                event_type="YAWNING", 
                event_key="YAWNING", 
                source_name="src",
                started_at_seconds=1.0, 
                ended_at_seconds=5.0, 
                max_severity=5, 
                occurrences=3, 
                last_message=""
            )
        ]
        result = engine.calculate_from_incidents(incidents)
        
        # YAWNING weight is 5. Occurrences = 3. Penalty = 15.
        # Score = 100 - 15 = 85.
        self.assertEqual(result.score, 85)
        self.assertEqual(result.severity_level, "LOW")

if __name__ == "__main__":
    unittest.main()
