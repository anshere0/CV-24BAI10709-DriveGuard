from __future__ import annotations

from dataclasses import dataclass, field
from typing import Dict, Iterable, List, TYPE_CHECKING

from cv_engine.event_engine import Event

if TYPE_CHECKING:
    from cv_engine.reporting import IncidentRecord


@dataclass
class ScoreResult:
    score: int
    penalties: Dict[str, int] = field(default_factory=dict)
    severity_level: str = "LOW"


class RiskEngine:
    def __init__(
        self,
        starting_score: int = 100,
        weights: Dict[str, int] = None,
        severity_thresholds: Dict[str, int] = None,
    ) -> None:
        self.starting_score = starting_score
        self.weights = weights or {
            "DROWSINESS": 20,
            "PHONE_USE": 15,
            "DISTRACTION": 12,
            "YAWNING": 5
        }
        self.severity_thresholds = severity_thresholds or {
            "LOW": 80,
            "MODERATE": 60,
            "HIGH": 40,
            "CRITICAL": 0
        }

    def _determine_severity(self, score: int) -> str:
        if score > self.severity_thresholds.get("LOW", 80):
            return "LOW"
        if score > self.severity_thresholds.get("MODERATE", 60):
            return "MODERATE"
        if score > self.severity_thresholds.get("HIGH", 40):
            return "HIGH"
        return "CRITICAL"

    def calculate(self, events: List[Event]) -> ScoreResult:
        penalties: Dict[str, int] = {}
        score = self.starting_score

        for event in events:
            # Override event's severity with the dynamically configured weight if available
            weight = self.weights.get(event.event_type, event.severity)
            penalties[event.event_type] = penalties.get(event.event_type, 0) + weight
            score -= weight

        final_score = max(score, 0)
        return ScoreResult(
            score=final_score,
            penalties=penalties,
            severity_level=self._determine_severity(final_score),
        )

    def calculate_from_incidents(self, incidents: Iterable["IncidentRecord"]) -> ScoreResult:
        penalties: Dict[str, int] = {}
        score = self.starting_score

        for incident in incidents:
            weight = self.weights.get(incident.event_type, incident.max_severity)
            # Apply weight per occurrence (simplified risk compounding)
            total_penalty = weight * incident.occurrences
            penalties[incident.event_type] = penalties.get(incident.event_type, 0) + total_penalty
            score -= total_penalty

        final_score = max(score, 0)
        return ScoreResult(
            score=final_score,
            penalties=penalties,
            severity_level=self._determine_severity(final_score),
        )
