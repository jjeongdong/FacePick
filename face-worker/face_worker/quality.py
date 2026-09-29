from face_worker.face import DetectedFace


def filter_faces(
    faces: list[DetectedFace], min_size: int, min_det_score: float
) -> list[DetectedFace]:
    # 작거나 불확실한 얼굴은 임베딩이 부정확해 엉뚱한 인물에 붙는다.
    # 생체정보 최소 보관을 위해 저장하지도 않는다.
    return [
        face for face in faces if face.short_side() >= min_size and face.det_score >= min_det_score
    ]
