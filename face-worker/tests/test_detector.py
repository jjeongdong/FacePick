import cv2
import numpy as np
import pytest

from face_worker.detector import FaceDetector, decode_image
from face_worker.errors import PermanentError


def jpeg(pixels: np.ndarray) -> bytes:
    ok, buffer = cv2.imencode(".jpg", pixels)
    assert ok
    return buffer.tobytes()


def test_decodes_jpeg_to_bgr_pixels():
    pixels = decode_image(jpeg(np.full((30, 40, 3), 200, dtype=np.uint8)))
    assert pixels.shape == (30, 40, 3)


@pytest.mark.parametrize("image", [b"", b"not an image", b"\xff\xd8\xff\xe0broken"])
def test_undecodable_bytes_are_permanent_failure(image):
    with pytest.raises(PermanentError) as error:
        decode_image(image)
    assert error.value.error_type == "UNDECODABLE_IMAGE"


@pytest.fixture(scope="module")
def detector() -> FaceDetector:
    return FaceDetector("buffalo_l")


@pytest.mark.integration
def test_detects_faces_in_group_photo(detector):
    from insightface.data import get_image

    group = get_image("t1")
    height, width = group.shape[:2]

    faces = detector.detect(jpeg(group))

    assert len(faces) >= 4
    for face in faces:
        assert face.embedding.shape == (512,)
        assert face.embedding.dtype == np.float32
        assert abs(float(np.linalg.norm(face.embedding)) - 1.0) < 1e-3
        x1, y1, x2, y2 = face.bbox
        assert 0 <= x1 < x2 <= width and 0 <= y1 < y2 <= height


@pytest.mark.integration
def test_blank_image_has_no_faces(detector):
    assert detector.detect(jpeg(np.full((480, 640, 3), 128, dtype=np.uint8))) == []


@pytest.mark.integration
def test_same_face_twice_has_near_identical_embedding(detector):
    from insightface.data import get_image

    image = jpeg(get_image("t1"))
    first = max(detector.detect(image), key=lambda f: f.det_score)
    second = max(detector.detect(image), key=lambda f: f.det_score)

    assert float(first.embedding @ second.embedding) > 0.99
