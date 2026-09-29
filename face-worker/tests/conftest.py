import numpy as np
import pytest

from face_worker.face import DetectedFace

DIMENSIONS = 512


def unit(*weights: float) -> np.ndarray:
    """앞 칸들에 weights 를 넣고 L2 정규화한 512차원 벡터. unit(1) 과 unit(0, 1) 은 유사도 0."""
    vector = np.zeros(DIMENSIONS, dtype=np.float32)
    vector[: len(weights)] = weights
    return vector / np.linalg.norm(vector)


@pytest.fixture
def unit_face():
    def make(*weights: float, det_score: float = 0.9, size: int = 100) -> DetectedFace:
        return DetectedFace(
            bbox=(10, 10, 10 + size, 10 + size), det_score=det_score, embedding=unit(*weights)
        )

    return make
