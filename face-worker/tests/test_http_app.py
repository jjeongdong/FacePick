import threading
import time

from fastapi.testclient import TestClient

from face_worker.errors import PermanentError
from face_worker.http_app import create_app
from face_worker.messages import PreviewReady
from face_worker.processor import ANALYZED, SKIPPED_DELETED, AnalysisResult

BODY = {"photoId": 7, "albumId": 3, "previewKey": "p/7.jpg"}


def client(analyze) -> TestClient:
    # 500 응답을 확인하려면 앱 예외를 테스트로 다시 던지지 않게 해야 한다.
    return TestClient(create_app(analyze), raise_server_exceptions=False)


def test_passes_message_and_returns_result():
    received: list[PreviewReady] = []

    def analyze(message):
        received.append(message)
        return AnalysisResult(ANALYZED, 2)

    response = client(analyze).post("/analyze", json=BODY)

    assert response.status_code == 200
    assert response.json() == {"photoId": 7, "result": "ANALYZED", "faceCount": 2}
    assert received == [PreviewReady(photo_id=7, album_id=3, preview_key="p/7.jpg")]


def test_skipped_photo_has_null_face_count():
    response = client(lambda message: AnalysisResult(SKIPPED_DELETED)).post("/analyze", json=BODY)

    assert response.status_code == 200
    assert response.json() == {"photoId": 7, "result": "SKIPPED_DELETED", "faceCount": None}


def test_permanent_error_is_422():
    def analyze(message):
        raise PermanentError("PREVIEW_MISSING", "p/7.jpg")

    response = client(analyze).post("/analyze", json=BODY)

    assert response.status_code == 422
    assert response.json() == {"errorType": "PREVIEW_MISSING", "message": "p/7.jpg"}


def test_unexpected_error_is_500():
    def analyze(message):
        raise RuntimeError("db down")

    response = client(analyze).post("/analyze", json=BODY)

    assert response.status_code == 500


def test_invalid_body_is_422():
    response = client(lambda message: AnalysisResult(ANALYZED, 0)).post(
        "/analyze", json={"photoId": 7, "albumId": 3, "previewKey": ""}
    )

    assert response.status_code == 422


def test_next_request_is_processed_after_failure():
    calls: list[int] = []

    def analyze(message):
        calls.append(message.photo_id)
        if message.photo_id == 1:
            raise PermanentError("PREVIEW_MISSING", "p/1.jpg")
        return AnalysisResult(ANALYZED, 1)

    test_client = client(analyze)
    test_client.post("/analyze", json={**BODY, "photoId": 1})
    response = test_client.post("/analyze", json={**BODY, "photoId": 2})

    assert response.status_code == 200
    assert calls == [1, 2]


def test_processes_one_request_at_a_time():
    active = 0
    max_active = 0
    guard = threading.Lock()

    def analyze(message):
        nonlocal active, max_active
        with guard:
            active += 1
            max_active = max(max_active, active)
        time.sleep(0.2)
        with guard:
            active -= 1
        return AnalysisResult(ANALYZED, 0)

    app = create_app(analyze)
    threads = [
        threading.Thread(
            target=lambda i=i: TestClient(app).post("/analyze", json={**BODY, "photoId": i})
        )
        for i in (1, 2)
    ]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()

    assert max_active == 1
