from confluent_kafka import KafkaError, Producer

FLUSH_TIMEOUT_SECONDS = 30


class DeliveryError(Exception):
    """브로커가 메시지를 받았는지 확인하지 못했다. 재시도 대상이다."""


def produce_and_wait(
    producer: Producer,
    topic: str,
    key: str | bytes | None,
    value: bytes | None,
    headers: list[tuple[str, bytes]] | None = None,
) -> None:
    # 오프셋 커밋 전에 전달을 확인해야 메시지를 잃지 않는다 (최소 1회 전달).
    failures: list[KafkaError] = []

    def on_delivery(error: KafkaError | None, _message) -> None:
        if error is not None:
            failures.append(error)

    producer.produce(topic, key=key, value=value, headers=headers, on_delivery=on_delivery)
    remaining = producer.flush(FLUSH_TIMEOUT_SECONDS)
    if remaining or failures:
        raise DeliveryError(f"{topic} 전달 실패: 미전달 {remaining}개, 오류 {failures}")
