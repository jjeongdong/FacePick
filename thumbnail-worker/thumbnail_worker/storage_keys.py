import re

from thumbnail_worker.errors import PermanentError

_ORIGINAL_KEY = re.compile(r"albums/(\d+)/originals/([0-9a-f]{64})")


def derived_keys(storage_key: str) -> tuple[str, str]:
    """원본 키에서 (썸네일 키, 미리보기 키)를 만든다. 재처리해도 같은 키라 덮어쓰기로 멱등이다."""
    match = _ORIGINAL_KEY.fullmatch(storage_key)
    if match is None:
        raise PermanentError("INVALID_STORAGE_KEY", f"원본 키 형식이 아니다: {storage_key}")
    album_id, content_hash = match.groups()
    return (
        f"albums/{album_id}/thumbnails/{content_hash}.jpg",
        f"albums/{album_id}/previews/{content_hash}.jpg",
    )
