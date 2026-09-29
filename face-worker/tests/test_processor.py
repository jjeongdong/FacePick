import json
from contextlib import contextmanager

import pytest

from face_worker.config import MatchSettings
from face_worker.errors import PermanentError
from face_worker.memory_album import InMemoryAlbum
from face_worker.processor import FaceProcessor

SETTINGS = MatchSettings(threshold=0.5, knn_k=5, min_size=40, min_det_score=0.6)
ALBUM_ID = 3


class FakeAlbum(InMemoryAlbum):
    def __init__(self, analyzed: set[int], purposes: dict[int, str]):
        super().__init__()
        self._analyzed = analyzed
        self._purposes = purposes
        self.marked: list[tuple[int, int]] = []
        self.face_photo_ids: list[int] = []
        self.saved_faces: list = []

    def is_analyzed(self, photo_id: int) -> bool:
        return photo_id in self._analyzed

    def photo_purpose(self, photo_id: int) -> str | None:
        return self._purposes.get(photo_id)

    def insert_face(self, photo_id, person_id, face):
        self.face_photo_ids.append(photo_id)
        self.saved_faces.append(face)
        return super().insert_face(photo_id, person_id, face)

    def mark_analyzed(self, photo_id: int, face_count: int) -> None:
        self._analyzed.add(photo_id)
        self.marked.append((photo_id, face_count))


class FakeRepository:
    def __init__(
        self, existing_photo_ids: set[int], selfie_photo_ids: frozenset[int] = frozenset()
    ):
        self.analyzed: set[int] = set()
        self.purposes = {
            photo_id: "SELFIE" if photo_id in selfie_photo_ids else "ALBUM"
            for photo_id in existing_photo_ids
        }
        self.album = FakeAlbum(self.analyzed, self.purposes)
        # 바깥 확인과 트랜잭션 안 확인 사이에 다른 워커가 끝낸 상황을 흉내 낸다.
        self.analyzed_by_other_worker: set[int] = set()
        # 바깥 확인과 트랜잭션 안 확인 사이(검출 중)에 사진이 삭제된 상황을 흉내 낸다.
        self.deleted_during_detection: set[int] = set()

    def is_analyzed(self, photo_id):
        return photo_id in self.analyzed

    def photo_exists(self, photo_id):
        return photo_id in self.purposes

    @contextmanager
    def album_transaction(self, album_id):
        self.analyzed |= self.analyzed_by_other_worker
        for photo_id in self.deleted_during_detection:
            self.purposes.pop(photo_id, None)
        yield self.album


class FakeStorage:
    def __init__(self, missing: bool = False):
        self.missing = missing
        self.downloads: list[str] = []

    def download(self, key):
        self.downloads.append(key)
        if self.missing:
            raise PermanentError("PREVIEW_MISSING", key)
        return b"jpeg"


class FakeDetector:
    def __init__(self, faces_by_call):
        self._faces_by_call = list(faces_by_call)

    def detect(self, image):
        return self._faces_by_call.pop(0)


def message(photo_id: int) -> bytes:
    return json.dumps(
        {
            "photoId": photo_id,
            "albumId": ALBUM_ID,
            "previewKey": f"p/{photo_id}.jpg",
            "width": 1,
            "height": 1,
        }
    ).encode()


def processor(repository, storage, detector) -> FaceProcessor:
    return FaceProcessor(repository, storage, detector, SETTINGS)


def test_saves_faces_and_marks_analyzed(unit_face):
    repository = FakeRepository({1})
    detector = FakeDetector([[unit_face(1, 0), unit_face(0, 1)]])

    processor(repository, FakeStorage(), detector).handle(message(1))

    assert repository.album.marked == [(1, 2)]
    assert repository.album.face_photo_ids == [1, 1]


def test_same_person_in_later_photo_joins_existing_person(unit_face):
    repository = FakeRepository({1, 2})
    detector = FakeDetector([[unit_face(1, 0)], [unit_face(1, 0.1)]])
    worker = processor(repository, FakeStorage(), detector)

    worker.handle(message(1))
    worker.handle(message(2))

    assert list(repository.album.covers) == [1]


def test_photo_without_faces_is_marked_with_zero():
    repository = FakeRepository({1})

    processor(repository, FakeStorage(), FakeDetector([[]])).handle(message(1))

    assert repository.album.marked == [(1, 0)]


def test_small_or_uncertain_faces_are_not_saved(unit_face):
    repository = FakeRepository({1})
    detector = FakeDetector([[unit_face(1, 0, size=30), unit_face(0, 1, det_score=0.3)]])

    processor(repository, FakeStorage(), detector).handle(message(1))

    assert repository.album.marked == [(1, 0)]
    assert repository.album.face_photo_ids == []


def test_already_analyzed_photo_is_skipped_without_download():
    repository = FakeRepository({1})
    repository.analyzed.add(1)
    storage = FakeStorage()

    processor(repository, storage, FakeDetector([])).handle(message(1))

    assert storage.downloads == []
    assert repository.album.marked == []


def test_deleted_photo_is_skipped_without_download():
    repository = FakeRepository(set())
    storage = FakeStorage()

    processor(repository, storage, FakeDetector([])).handle(message(1))

    assert storage.downloads == []
    assert repository.album.marked == []


def test_photo_finished_by_other_worker_is_not_saved_twice(unit_face):
    repository = FakeRepository({1})
    repository.analyzed_by_other_worker.add(1)

    processor(repository, FakeStorage(), FakeDetector([[unit_face(1, 0)]])).handle(message(1))

    assert repository.album.face_photo_ids == []
    assert repository.album.marked == []


def test_photo_deleted_during_detection_is_not_saved(unit_face):
    repository = FakeRepository({1})
    repository.deleted_during_detection.add(1)

    processor(repository, FakeStorage(), FakeDetector([[unit_face(1, 0)]])).handle(message(1))

    assert repository.album.face_photo_ids == []
    assert repository.album.marked == []


def test_selfie_keeps_only_largest_face(unit_face):
    repository = FakeRepository({1}, selfie_photo_ids=frozenset({1}))
    detector = FakeDetector(
        [[unit_face(1, 0, size=60), unit_face(0, 1, size=200), unit_face(1, 1, size=90)]]
    )

    processor(repository, FakeStorage(), detector).handle(message(1))

    assert [face.short_side() for face in repository.album.saved_faces] == [200]
    assert repository.album.marked == [(1, 1)]


def test_selfie_without_usable_face_is_marked_with_zero(unit_face):
    repository = FakeRepository({1}, selfie_photo_ids=frozenset({1}))
    detector = FakeDetector([[unit_face(1, 0, size=30)]])

    processor(repository, FakeStorage(), detector).handle(message(1))

    assert repository.album.saved_faces == []
    assert repository.album.marked == [(1, 0)]


def test_album_photo_keeps_all_faces(unit_face):
    repository = FakeRepository({1})
    detector = FakeDetector([[unit_face(1, 0, size=60), unit_face(0, 1, size=200)]])

    processor(repository, FakeStorage(), detector).handle(message(1))

    assert repository.album.marked == [(1, 2)]


def test_missing_preview_is_permanent_failure():
    repository = FakeRepository({1})

    with pytest.raises(PermanentError) as error:
        processor(repository, FakeStorage(missing=True), FakeDetector([])).handle(message(1))
    assert error.value.error_type == "PREVIEW_MISSING"


def test_invalid_message_is_permanent_failure():
    with pytest.raises(PermanentError):
        processor(FakeRepository({1}), FakeStorage(), FakeDetector([])).handle(b"not json")
