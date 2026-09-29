import threading
import time

from fastapi.testclient import TestClient

from thumbnail_worker.errors import PermanentError
from thumbnail_worker.http_app import create_app
from thumbnail_worker.messages import PreviewReady


def client(process) -> TestClient:
    # 500 응답을 확인하려면 앱 예외를 테스트로 다시 던지지 않게 해야 한다.
    return TestClient(create_app(process), raise_server_exceptions=False)


def test_returns_preview_ready_as_json():
    response = client(lambda photo_id: PreviewReady(photo_id, 3, "p.jpg", 640, 480)).post(
        "/process", json={"photoId": 7}
    )

    assert response.status_code == 200
    assert response.json() == {
        "photoId": 7,
        "albumId": 3,
        "previewKey": "p.jpg",
        "width": 640,
        "height": 480,
    }


def test_missing_photo_is_404():
    response = client(lambda photo_id: None).post("/process", json={"photoId": 7})

    assert response.status_code == 404
    assert response.json()["errorType"] == "PHOTO_NOT_FOUND"


def test_permanent_error_is_422():
    def process(photo_id):
        raise PermanentError("UNDECODABLE_IMAGE", "깨진 파일")

    response = client(process).post("/process", json={"photoId": 7})

    assert response.status_code == 422
    assert response.json() == {"errorType": "UNDECODABLE_IMAGE", "message": "깨진 파일"}


def test_unexpected_error_is_500():
    def process(photo_id):
        raise RuntimeError("db down")

    response = client(process).post("/process", json={"photoId": 7})

    assert response.status_code == 500


def test_invalid_body_is_422():
    response = client(lambda photo_id: None).post("/process", json={"photoId": 0})

    assert response.status_code == 422


def test_next_request_is_processed_after_failure():
    calls: list[int] = []

    def process(photo_id):
        calls.append(photo_id)
        if photo_id == 1:
            raise PermanentError("UNDECODABLE_IMAGE", "깨진 파일")
        return PreviewReady(photo_id, 3, "p.jpg", 1, 1)

    test_client = client(process)
    test_client.post("/process", json={"photoId": 1})
    response = test_client.post("/process", json={"photoId": 2})

    assert response.status_code == 200
    assert calls == [1, 2]


def test_processes_one_request_at_a_time():
    active = 0
    max_active = 0
    guard = threading.Lock()

    def process(photo_id):
        nonlocal active, max_active
        with guard:
            active += 1
            max_active = max(max_active, active)
        time.sleep(0.2)
        with guard:
            active -= 1
        return PreviewReady(photo_id, 3, "p.jpg", 1, 1)

    app = create_app(process)
    threads = [
        threading.Thread(target=lambda i=i: TestClient(app).post("/process", json={"photoId": i}))
        for i in (1, 2)
    ]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()

    assert max_active == 1
