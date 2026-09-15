from __future__ import annotations

from dataclasses import dataclass
from typing import Dict, List, Optional, Sequence, Tuple

from ultralytics import YOLO


@dataclass
class Detection:
    label: str
    confidence: float
    bbox: Tuple[int, int, int, int]


class YoloDetector:
    def __init__(
        self,
        model_path: str,
        confidence_threshold: float = 0.25,
        label_map: Optional[Dict[str, str]] = None,
        allowed_labels: Optional[Sequence[str]] = None,
    ) -> None:
        self.model_path = model_path
        self.confidence_threshold = confidence_threshold
        self.label_map = label_map or {}
        self.allowed_labels = set(allowed_labels) if allowed_labels is not None else None
        self._model = YOLO(model_path)

    @property
    def class_names(self) -> Sequence[str]:
        names = self._model.names
        if isinstance(names, dict):
            return [names[i] for i in sorted(names)]
        return names

    def detect(self, frame) -> List[Detection]:
        results = self._model(frame, stream=True, verbose=False)
        detections: List[Detection] = []

        for result in results:
            for box in result.boxes:
                confidence = float(box.conf[0])
                if confidence < self.confidence_threshold:
                    continue

                x1, y1, x2, y2 = map(int, box.xyxy[0].tolist())
                cls = int(box.cls[0])
                raw_label = self.class_names[cls]
                label = self.label_map.get(raw_label, raw_label)
                if self.allowed_labels is not None and label not in self.allowed_labels:
                    continue
                detections.append(
                    Detection(
                        label=label,
                        confidence=confidence,
                        bbox=(x1, y1, x2, y2),
                    )
                )

        return detections
