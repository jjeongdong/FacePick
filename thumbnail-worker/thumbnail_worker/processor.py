import logging
from datetime import datetime

from confluent_kafka import Producer

from thumbnail_worker.imaging import render
from thumbnail_worker.messages import PreviewReady, parse_photo_uploaded
from thumbnail_worker.photo_repository import PhotoRepository
from thumbnail_worker.publisher import produce_and_wait
from thumbnail_worker.storage import PhotoStorage
from thumbnail_worker.storage_keys import derived_keys

log = logging.getLogger(__name__)


class PhotoProcessor:
    def __init__(
        self,
        repository: PhotoRepository,
        storage: PhotoStorage,
        producer: Producer | None = None,
        preview_ready_topic: str | None = None,
    ):
        # HTTP 모드는 결과를 응답으로 돌려주므로 producer 없이 만든다.
        self._repository = repository
        self._storage = storage
        self._producer = producer
        self._preview_ready_topic = preview_ready_topic

    def handle(self, value: bytes | None) -> None:
        message = parse_photo_uploaded(value)
        result = self.process(message.photo_id)
        if result is not None:
            self._publish(result)

    def process(self, photo_id: int) -> PreviewReady | None:
        """썸네일·미리보기를 만들고 photos 를 갱신한다. 발행은 하지 않는다. 행이 없으면 None."""
        row = self._repository.find(photo_id)
        if row is None:
            log.warning("photos 에 행이 없어 건너뛴다: photoId=%s", photo_id)
            return None

        if row.processed_at is not None:
            # 다시 만들지 않고 저장된 값을 돌려준다. Kafka 모드는 UPDATE 커밋 뒤 발행 전에
            # 죽었을 수 있어 이 값을 다시 발행하고, HTTP 모드는 재시도 요청에 이 값을 응답한다.
            log.info("이미 처리된 사진이라 저장된 값을 돌려준다: photoId=%s", row.photo_id)
            return PreviewReady(row.photo_id, row.album_id, row.preview_key, row.width, row.height)

        thumbnail_key, preview_key = derived_keys(row.storage_key)
        renditions = render(self._storage.download(row.storage_key))
        self._storage.upload_jpeg(thumbnail_key, renditions.thumbnail)
        self._storage.upload_jpeg(preview_key, renditions.preview)
        # 백엔드가 LocalDateTime.now()(로컬 시각)를 쓰므로 DB now()(UTC) 대신 로컬 시각을 넣는다.
        self._repository.mark_processed(
            row.photo_id,
            thumbnail_key,
            preview_key,
            renditions.width,
            renditions.height,
            renditions.taken_at,
            datetime.now(),
        )
        log.info("처리 완료: photoId=%s %sx%s", row.photo_id, renditions.width, renditions.height)
        return PreviewReady(
            row.photo_id, row.album_id, preview_key, renditions.width, renditions.height
        )

    def _publish(self, event: PreviewReady) -> None:
        produce_and_wait(self._producer, self._preview_ready_topic, event.key(), event.to_json())
