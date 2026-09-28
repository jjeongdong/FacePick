import logging
import signal
import time

from confluent_kafka import Consumer, Message, Producer, TopicPartition

from thumbnail_worker.config import Config
from thumbnail_worker.errors import PermanentError
from thumbnail_worker.photo_repository import PhotoRepository
from thumbnail_worker.processor import PhotoProcessor
from thumbnail_worker.publisher import produce_and_wait
from thumbnail_worker.storage import PhotoStorage

log = logging.getLogger(__name__)

MAX_BACKOFF_SECONDS = 30
MAX_ERROR_MESSAGE_LENGTH = 1000


def backoff_seconds(failures: int) -> int:
    """연속 실패 횟수(1부터)에 대한 대기 시간. 1, 2, 4 … 최대 30초."""
    return min(2 ** (failures - 1), MAX_BACKOFF_SECONDS)


def dlq_headers(
    error: PermanentError, topic: str, partition: int, offset: int
) -> list[tuple[str, bytes]]:
    return [
        ("error-type", error.error_type.encode()),
        ("error-message", str(error)[:MAX_ERROR_MESSAGE_LENGTH].encode()),
        ("source-topic", topic.encode()),
        ("source-partition", str(partition).encode()),
        ("source-offset", str(offset).encode()),
    ]


def run(config: Config) -> None:
    consumer = Consumer(
        {
            "bootstrap.servers": config.kafka_bootstrap,
            "group.id": config.kafka_group_id,
            "enable.auto.commit": False,
            "auto.offset.reset": "earliest",
        }
    )
    producer = Producer(
        {"bootstrap.servers": config.kafka_bootstrap, "acks": "all", "enable.idempotence": True}
    )
    processor = PhotoProcessor(
        PhotoRepository(config.database_url),
        PhotoStorage(
            config.s3_endpoint, config.s3_access_key, config.s3_secret_key, config.s3_bucket
        ),
        producer,
        config.topic_preview_ready,
    )

    stopping = False

    def stop(_signum, _frame) -> None:
        nonlocal stopping
        stopping = True
        log.info("종료 신호를 받았다. 처리 중인 메시지를 끝내고 멈춘다")

    signal.signal(signal.SIGINT, stop)
    signal.signal(signal.SIGTERM, stop)

    consumer.subscribe([config.topic_photo_uploaded])
    log.info("썸네일 워커 시작: %s 구독", config.topic_photo_uploaded)
    failures = 0
    try:
        while not stopping:
            msg = consumer.poll(1.0)
            if msg is None:
                continue
            if msg.error():
                log.warning("Kafka 수신 오류: %s", msg.error())
                continue
            try:
                _process(processor, producer, config.topic_dlq, msg)
                consumer.commit(message=msg, asynchronous=False)
                failures = 0
            except Exception:
                failures += 1
                wait = backoff_seconds(failures)
                # 스택트레이스는 연속 실패의 첫 번째만 남긴다.
                # 장애 동안 로그가 스택트레이스로 뒤덮이지 않게 하기 위해서다.
                log.warning(
                    "처리 실패, %s초 뒤 다시 시도한다 (연속 %s번째): partition=%s offset=%s",
                    wait,
                    failures,
                    msg.partition(),
                    msg.offset(),
                    exc_info=failures == 1,
                )
                # 커밋하지 않고 되돌린 뒤 poll 로 돌아가야
                # max.poll.interval 초과로 그룹에서 빠지지 않는다.
                consumer.seek(TopicPartition(msg.topic(), msg.partition(), msg.offset()))
                _sleep_unless(lambda: stopping, wait)
    finally:
        consumer.close()
        producer.flush(10)
        log.info("썸네일 워커 종료")


def _process(processor: PhotoProcessor, producer: Producer, dlq_topic: str, msg: Message) -> None:
    try:
        processor.handle(msg.value())
    except PermanentError as e:
        log.error(
            "영구 실패라 DLQ 로 보낸다: partition=%s offset=%s type=%s %s",
            msg.partition(),
            msg.offset(),
            e.error_type,
            e,
        )
        produce_and_wait(
            producer,
            dlq_topic,
            msg.key(),
            msg.value(),
            dlq_headers(e, msg.topic(), msg.partition(), msg.offset()),
        )


def _sleep_unless(should_stop, seconds: int) -> None:
    # 백오프 중에도 종료 신호에 바로 반응하도록 1초씩 잔다.
    for _ in range(seconds):
        if should_stop():
            return
        time.sleep(1)
