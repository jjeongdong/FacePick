import cv2
import numpy as np

from face_worker.errors import PermanentError
from face_worker.face import DetectedFace, clamp_bbox

# 미리보기(긴 변 2048)를 이 크기로 줄여 검출한다. InsightFace 기본값이며 CPU 에서 1초 안쪽.
DET_SIZE = (640, 640)
# 짧은 변이 이보다 작으면 품질 필터(최소 40px)를 넘는 얼굴이 있을 수 없다.
# 극단적으로 가는 이미지는 DET_SIZE 로 줄일 때 짧은 변이 0 이 되어 InsightFace 가 cv2.error 를
# 던지므로(일시 오류로 분류돼 무한 재시도), 모델에 넣기 전에 얼굴 없음으로 끝낸다.
MIN_DETECTABLE_SIDE = 32


def decode_image(image: bytes) -> np.ndarray:
    # 빈 버퍼는 imdecode 가 None 대신 cv2.error 를 던지므로 둘 다 영구 실패로 바꾼다.
    try:
        pixels = cv2.imdecode(np.frombuffer(image, dtype=np.uint8), cv2.IMREAD_COLOR)
    except cv2.error as e:
        raise PermanentError("UNDECODABLE_IMAGE", f"미리보기를 이미지로 읽을 수 없다: {e}") from e
    if pixels is None:
        raise PermanentError("UNDECODABLE_IMAGE", "미리보기를 이미지로 읽을 수 없다")
    return pixels


class FaceDetector:
    def __init__(self, model_name: str):
        # import 만으로 onnxruntime 을 올리므로,
        # 단위 테스트가 decode_image 만 쓸 때는 불러오지 않게 여기서 가져온다.
        from insightface.app import FaceAnalysis

        self._app = FaceAnalysis(
            name=model_name,
            allowed_modules=["detection", "recognition"],
            providers=["CPUExecutionProvider"],
        )
        self._app.prepare(ctx_id=-1, det_size=DET_SIZE)

    def detect(self, image: bytes) -> list[DetectedFace]:
        pixels = decode_image(image)
        height, width = pixels.shape[:2]
        if min(height, width) < MIN_DETECTABLE_SIDE:
            return []
        return [
            DetectedFace(
                bbox=clamp_bbox(tuple(face.bbox), width, height),
                det_score=float(face.det_score),
                embedding=face.normed_embedding.astype(np.float32),
            )
            for face in self._app.get(pixels)
        ]
