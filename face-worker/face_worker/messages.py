import json
from dataclasses import dataclass

from face_worker.errors import PermanentError


@dataclass(frozen=True)
class PreviewReady:
    photo_id: int
    album_id: int
    preview_key: str


def parse_preview_ready(value: bytes | None) -> PreviewReady:
    try:
        data = json.loads(value)
    except (TypeError, ValueError) as e:
        raise PermanentError("INVALID_MESSAGE", f"JSON 으로 읽을 수 없다: {e}") from e
    if not isinstance(data, dict):
        raise PermanentError("INVALID_MESSAGE", "JSON 객체가 아니다")

    photo_id = data.get("photoId")
    album_id = data.get("albumId")
    preview_key = data.get("previewKey")
    if not (_is_id(photo_id) and _is_id(album_id) and isinstance(preview_key, str) and preview_key):
        raise PermanentError("INVALID_MESSAGE", f"필수 필드가 없거나 형식이 틀리다: {data}")
    return PreviewReady(photo_id=photo_id, album_id=album_id, preview_key=preview_key)


def _is_id(value: object) -> bool:
    # bool 은 int 의 하위 타입이라 따로 막는다.
    return isinstance(value, int) and not isinstance(value, bool) and value > 0
