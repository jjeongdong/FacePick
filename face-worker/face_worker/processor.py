import logging
from dataclasses import dataclass

from face_worker.assigner import assign_faces
from face_worker.config import MatchSettings
from face_worker.detector import FaceDetector
from face_worker.face_repository import FaceRepository
from face_worker.messages import PreviewReady, parse_preview_ready
from face_worker.quality import filter_faces
from face_worker.storage import PreviewStorage

log = logging.getLogger(__name__)

# 셀피("내 사진 등록")는 photos.purpose 로 구분한다 (백엔드 PhotoPurpose).
SELFIE_PURPOSE = "SELFIE"

# HTTP 모드가 응답으로 돌려주는 분석 결과. Kafka 모드는 쓰지 않는다.
ANALYZED = "ANALYZED"
ALREADY_ANALYZED = "ALREADY_ANALYZED"
SKIPPED_DELETED = "SKIPPED_DELETED"


@dataclass(frozen=True)
class AnalysisResult:
    status: str
    face_count: int | None = None


class FaceProcessor:
    def __init__(
        self,
        repository: FaceRepository,
        storage: PreviewStorage,
        detector: FaceDetector,
        settings: MatchSettings,
    ):
        self._repository = repository
        self._storage = storage
        self._detector = detector
        self._settings = settings

    def handle(self, value: bytes | None) -> None:
        self.analyze(parse_preview_ready(value))

    def analyze(self, message: PreviewReady) -> AnalysisResult:
        # Outbox·재시도 때문에 같은 메시지가 여러 번 온다. 모델을 돌리기 전에 걸러 낸다.
        if self._repository.is_analyzed(message.photo_id):
            log.info("이미 분석한 사진이라 건너뛴다: photoId=%s", message.photo_id)
            return AnalysisResult(ALREADY_ANALYZED)
        if not self._repository.photo_exists(message.photo_id):
            log.info("삭제된 사진이라 건너뛴다: photoId=%s", message.photo_id)
            return AnalysisResult(SKIPPED_DELETED)

        # 추론은 느리므로 트랜잭션(앨범 잠금) 밖에서 한다.
        image = self._storage.download(message.preview_key)
        faces = filter_faces(
            self._detector.detect(image), self._settings.min_size, self._settings.min_det_score
        )

        with self._repository.album_transaction(message.album_id) as album:
            # 잠금을 기다리는 사이 다른 워커가 같은 사진을 끝냈을 수 있다.
            if album.is_analyzed(message.photo_id):
                log.info("다른 워커가 먼저 분석했다: photoId=%s", message.photo_id)
                return AnalysisResult(ALREADY_ANALYZED)
            # 검출하는 사이 사진이 삭제됐을 수 있다. 백엔드는 같은 앨범 잠금을 잡고 얼굴을 지우므로
            # 잠금 안에서 확인하면, 삭제가 먼저 커밋된 경우를 빠짐없이 걸러 낸다.
            purpose = album.photo_purpose(message.photo_id)
            if purpose is None:
                log.info("분석 중 삭제된 사진이라 건너뛴다: photoId=%s", message.photo_id)
                return AnalysisResult(SKIPPED_DELETED)
            if purpose == SELFIE_PURPOSE and faces:
                # 셀피는 찍는 사람이 가장 크게 나온다.
                # 뒤에 찍힌 다른 사람 얼굴은 인물 묶기에 넣지 않는다.
                faces = [max(faces, key=lambda face: face.short_side())]
            person_ids = assign_faces(
                album, message.photo_id, faces, self._settings.threshold, self._settings.knn_k
            )
            album.mark_analyzed(message.photo_id, len(faces))
        log.info(
            "얼굴 분석 완료: photoId=%s albumId=%s faces=%s personIds=%s",
            message.photo_id,
            message.album_id,
            len(faces),
            person_ids,
        )
        return AnalysisResult(ANALYZED, len(faces))
