from dataclasses import dataclass
from datetime import datetime
from io import BytesIO

import pillow_heif
from PIL import Image, ImageOps

from thumbnail_worker.errors import PermanentError

pillow_heif.register_heif_opener()

THUMBNAIL_MAX_SIDE = 400
PREVIEW_MAX_SIDE = 2048
JPEG_QUALITY = 85

_EXIF_IFD = 0x8769
_DATETIME_ORIGINAL = 0x9003


@dataclass(frozen=True)
class Renditions:
    thumbnail: bytes
    preview: bytes
    width: int
    height: int
    taken_at: datetime | None


def render(original: bytes) -> Renditions:
    try:
        with Image.open(BytesIO(original)) as source:
            taken_at = _taken_at(source)
            # CMYK·흑백 프로파일을 RGB JPEG 에 넣으면 색이 틀어지므로 RGB 계열만 옮긴다.
            icc_profile = source.info.get("icc_profile") if source.mode in ("RGB", "RGBA") else None
            image = _to_rgb(ImageOps.exif_transpose(source))
    except (Image.DecompressionBombError, OSError, SyntaxError, ValueError) as e:
        # UnidentifiedImageError 는 OSError, 잘린 파일은 load 중 OSError 로 온다.
        raise PermanentError("UNDECODABLE_IMAGE", f"이미지를 읽을 수 없다: {e}") from e

    preview = _shrunk(image, PREVIEW_MAX_SIDE)
    # 큰 원본 대신 미리보기에서 줄여 LANCZOS 비용을 줄인다.
    thumbnail = _shrunk(preview, THUMBNAIL_MAX_SIDE)
    return Renditions(
        thumbnail=_jpeg(thumbnail, icc_profile),
        preview=_jpeg(preview, icc_profile),
        width=image.width,
        height=image.height,
        taken_at=taken_at,
    )


def _to_rgb(image: Image.Image) -> Image.Image:
    has_alpha = image.mode in ("RGBA", "LA") or (image.mode == "P" and "transparency" in image.info)
    if not has_alpha:
        return image.convert("RGB")
    # 그냥 RGB 로 바꾸면 투명 영역이 검게 나온다.
    rgba = image.convert("RGBA")
    background = Image.new("RGB", rgba.size, (255, 255, 255))
    background.paste(rgba, mask=rgba.getchannel("A"))
    return background


def _shrunk(image: Image.Image, max_side: int) -> Image.Image:
    copy = image.copy()
    # thumbnail() 은 비율을 지키고 원본보다 키우지 않는다.
    copy.thumbnail((max_side, max_side), Image.Resampling.LANCZOS)
    return copy


def _jpeg(image: Image.Image, icc_profile: bytes | None) -> bytes:
    buffer = BytesIO()
    # exif 인자를 주지 않아 GPS 등 메타데이터가 공유용 파일에 실리지 않는다.
    image.save(buffer, "JPEG", quality=JPEG_QUALITY, icc_profile=icc_profile)
    return buffer.getvalue()


def _taken_at(source: Image.Image) -> datetime | None:
    try:
        raw = source.getexif().get_ifd(_EXIF_IFD).get(_DATETIME_ORIGINAL)
        return datetime.strptime(raw.strip("\x00 "), "%Y:%m:%d %H:%M:%S")
    except Exception:
        # 촬영 시각은 부가 정보라, 없거나 깨졌다고 사진 처리를 실패시키지 않는다.
        return None
