import unittest
from unittest.mock import patch
from cv_engine.face_monitor import FaceMonitor, LEFT_EYE_INDICES, MOUTH_HEIGHT_INDICES, MOUTH_WIDTH_INDICES

class Landmark:
    def __init__(self, x, y):
        self.x = x
        self.y = y

class CVUtilsTests(unittest.TestCase):
    @patch('cv_engine.face_monitor.mp_vision.FaceLandmarker.create_from_options')
    def setUp(self, mock_create):
        self.monitor = FaceMonitor(model_path="dummy")

    def test_calculate_ear(self):
        # We need a mock landmarks list where the LEFT_EYE_INDICES are placed.
        # LEFT_EYE_INDICES = [33, 160, 158, 133, 153, 144]
        # p1=33, p2=160, p3=158, p4=133, p5=153, p6=144
        landmarks = [Landmark(0, 0)] * 500
        landmarks[33] = Landmark(0, 0)
        landmarks[160] = Landmark(2, 2)
        landmarks[158] = Landmark(8, 2)
        landmarks[133] = Landmark(10, 0)
        landmarks[153] = Landmark(8, -2)
        landmarks[144] = Landmark(2, -2)

        ear = self.monitor._compute_eye_aspect_ratio(landmarks, LEFT_EYE_INDICES)
        # EAR = (4 + 4) / (2 * 10) = 0.4
        self.assertAlmostEqual(ear, 0.4, places=2)

    def test_calculate_mar(self):
        # MOUTH_WIDTH_INDICES = (78, 308)
        # MOUTH_HEIGHT_INDICES = (13, 14)
        landmarks = [Landmark(0, 0)] * 500
        landmarks[78] = Landmark(0, 0)   # left
        landmarks[308] = Landmark(8, 0)  # right
        landmarks[13] = Landmark(4, 2)   # upper
        landmarks[14] = Landmark(4, -2)  # lower
        
        mar = self.monitor._compute_mouth_open_ratio(landmarks)
        # Width = 8, Height = 4
        # MAR = 4 / 8 = 0.5
        self.assertAlmostEqual(mar, 0.5, places=2)

if __name__ == "__main__":
    unittest.main()
