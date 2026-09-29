import numpy as np

from face_worker.face import DetectedFace
from face_worker.quality import filter_faces


def face(width: int, height: int, det_score: float) -> DetectedFace:
    return DetectedFace(
        bbox=(0, 0, width, height), det_score=det_score, embedding=np.ones(512, dtype=np.float32)
    )


def test_keeps_faces_at_or_above_limits():
    kept = face(40, 80, 0.6)
    assert filter_faces([kept], min_size=40, min_det_score=0.6) == [kept]


def test_drops_small_or_uncertain_faces():
    faces = [face(39, 200, 0.99), face(200, 39, 0.99), face(100, 100, 0.59)]
    assert filter_faces(faces, min_size=40, min_det_score=0.6) == []
