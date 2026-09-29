from dataclasses import dataclass

import numpy as np


# ndarray 는 == 가 배열을 돌려주므로 eq 를 끈다.
@dataclass(frozen=True, eq=False)
class DetectedFace:
    bbox: tuple[int, int, int, int]  # x1, y1, x2, y2 (미리보기 픽셀)
    det_score: float
    embedding: np.ndarray  # L2 정규화된 512차원 float32

    def short_side(self) -> int:
        x1, y1, x2, y2 = self.bbox
        return min(x2 - x1, y2 - y1)


def clamp_bbox(
    bbox: tuple[float, float, float, float], width: int, height: int
) -> tuple[int, int, int, int]:
    # 사진 가장자리에 걸린 얼굴은 검출기가 이미지 밖 좌표를 준다.
    # 나중에 얼굴을 잘라 보여줄 때 쓰므로 안쪽으로 맞춘다.
    x1, y1, x2, y2 = (round(value) for value in bbox)
    return (
        min(max(x1, 0), width),
        min(max(y1, 0), height),
        min(max(x2, 0), width),
        min(max(y2, 0), height),
    )
