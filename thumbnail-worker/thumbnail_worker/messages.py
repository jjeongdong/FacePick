import json
from dataclasses import dataclass

from thumbnail_worker.errors import PermanentError


@dataclass(frozen=True)
class PhotoUploaded:
    photo_id: int
    album_id: int
    storage_key: str


@dataclass(frozen=True)
class PreviewReady:
    photo_id: int
    album_id: int
    preview_key: str
    width: int
    height: int

    def key(self) -> str:
        # 입력과 같은 키라 한 앨범의 메시지가 같은 파티션에서 순서대로 face-worker 에 간다.
        return str(self.album_id)

    def to_json(self) -> bytes:
        return json.dumps(
            {
                "photoId": self.photo_id,
                "albumId": self.album_id,
                "previewKey": self.preview_key,
                "width": self.width,
                "height": self.height,
            }
        ).encode()


def parse_photo_uploaded(value: bytes | None) -> PhotoUploaded:
    try:
        data = json.loads(value)
    except (TypeError, ValueError) as e:
        raise PermanentError("INVALID_MESSAGE", f"JSON 으로 읽을 수 없다: {e}") from e
    if not isinstance(data, dict):
        raise PermanentError("INVALID_MESSAGE", "JSON 객체가 아니다")

    photo_id = data.get("photoId")
    album_id = data.get("albumId")
    storage_key = data.get("storageKey")
    if not (_is_id(photo_id) and _is_id(album_id) and isinstance(storage_key, str) and storage_key):
        raise PermanentError("INVALID_MESSAGE", f"필수 필드가 없거나 형식이 틀리다: {data}")
    return PhotoUploaded(photo_id=photo_id, album_id=album_id, storage_key=storage_key)


def _is_id(value: object) -> bool:
    # bool 은 int 의 하위 타입이라 따로 막는다.
    return isinstance(value, int) and not isinstance(value, bool) and value > 0
