from datetime import datetime
from io import BytesIO

import pytest
from PIL import Image, ImageCms

from thumbnail_worker.errors import PermanentError
from thumbnail_worker.imaging import render

ORIENTATION = 0x0112
EXIF_IFD = 0x8769
DATETIME_ORIGINAL = 0x9003
GPS_IFD = 0x8825


def encode(image: Image.Image, fmt: str = "JPEG", **params) -> bytes:
    buffer = BytesIO()
    image.save(buffer, fmt, **params)
    return buffer.getvalue()


def exif_with(orientation: int | None = None, taken_at: str | None = None) -> Image.Exif:
    exif = Image.Exif()
    if orientation is not None:
        exif[ORIENTATION] = orientation
    if taken_at is not None:
        exif[EXIF_IFD] = {DATETIME_ORIGINAL: taken_at}
    return exif


def opened(data: bytes) -> Image.Image:
    image = Image.open(BytesIO(data))
    image.load()
    return image


def test_long_side_is_2048_and_400():
    result = render(encode(Image.new("RGB", (4000, 3000), "red")))

    assert (result.width, result.height) == (4000, 3000)
    assert opened(result.preview).size == (2048, 1536)
    assert opened(result.thumbnail).size == (400, 300)
    assert opened(result.preview).format == "JPEG"


def test_small_image_is_not_upscaled():
    result = render(encode(Image.new("RGB", (300, 200), "red")))

    assert opened(result.preview).size == (300, 200)
    assert opened(result.thumbnail).size == (300, 200)


def test_exif_orientation_is_applied():
    # Orientation=6: 센서는 가로로 저장했지만 90도 돌려 보여야 하는 세로 사진
    original = encode(Image.new("RGB", (3000, 2000), "red"), exif=exif_with(orientation=6))

    result = render(original)

    assert (result.width, result.height) == (2000, 3000)
    preview = opened(result.preview)
    assert max(preview.size) == 2048 and preview.width < preview.height
    thumbnail = opened(result.thumbnail)
    assert max(thumbnail.size) == 400 and thumbnail.width < thumbnail.height


def test_heic_is_decoded():
    original = encode(Image.new("RGB", (640, 480), "blue"), "HEIF")

    result = render(original)

    assert (result.width, result.height) == (640, 480)
    assert opened(result.preview).format == "JPEG"


def test_transparent_png_becomes_white():
    original = encode(Image.new("RGBA", (50, 50), (0, 0, 0, 0)), "PNG")

    result = render(original)

    r, g, b = opened(result.preview).getpixel((25, 25))
    assert min(r, g, b) >= 245


def test_output_has_no_exif():
    exif = exif_with(orientation=1, taken_at="2026:09:01 10:20:30")
    exif[GPS_IFD] = {1: "N"}
    original = encode(Image.new("RGB", (800, 600), "red"), exif=exif)

    result = render(original)

    assert len(opened(result.preview).getexif()) == 0
    assert len(opened(result.thumbnail).getexif()) == 0


def test_output_has_no_jpeg_comment():
    original = encode(Image.new("RGB", (800, 600), "red"), comment=b"secret-comment GPS 37.55")

    result = render(original)

    assert "comment" not in opened(result.preview).info
    assert "comment" not in opened(result.thumbnail).info


def test_icc_profile_is_kept():
    icc = ImageCms.ImageCmsProfile(ImageCms.createProfile("sRGB")).tobytes()
    original = encode(Image.new("RGB", (800, 600), "red"), icc_profile=icc)

    result = render(original)

    assert opened(result.preview).info.get("icc_profile") == icc
    assert opened(result.thumbnail).info.get("icc_profile") == icc


def test_taken_at_from_datetime_original():
    original = encode(Image.new("RGB", (100, 100)), exif=exif_with(taken_at="2026:09:01 10:20:30"))

    assert render(original).taken_at == datetime(2026, 9, 1, 10, 20, 30)


@pytest.mark.parametrize("raw", [None, "0000:00:00 00:00:00", "2026-09-01 10:20:30", "garbage"])
def test_missing_or_invalid_taken_at_is_none(raw):
    original = encode(Image.new("RGB", (100, 100)), exif=exif_with(taken_at=raw))

    assert render(original).taken_at is None


@pytest.mark.parametrize("data", [b"", b"not an image", b"\x89PNG\r\n\x1a\n broken"])
def test_undecodable_bytes_are_permanent_failure(data):
    with pytest.raises(PermanentError) as error:
        render(data)
    assert error.value.error_type == "UNDECODABLE_IMAGE"


def test_truncated_jpeg_is_permanent_failure():
    complete = encode(Image.effect_noise((800, 600), 64).convert("RGB"))

    with pytest.raises(PermanentError) as error:
        render(complete[: len(complete) // 2])
    assert error.value.error_type == "UNDECODABLE_IMAGE"


def test_multi_page_tiff_like_dng_is_unsupported():
    # DNG 는 TIFF 기반이고 IFD0 에 작은 미리보기를 둔다. Pillow 는 그 미리보기만 읽어
    # 256px 결과로 "성공"해 버리므로 형식으로 막는다.
    small_preview = Image.new("RGB", (256, 171), "red")
    original = encode(
        small_preview, "TIFF", save_all=True, append_images=[Image.new("RGB", (6000, 4000))]
    )

    with pytest.raises(PermanentError) as error:
        render(original)
    assert error.value.error_type == "UNSUPPORTED_FORMAT"


def test_decompression_bomb_is_permanent_failure(monkeypatch):
    # 실제 1.8억 픽셀 이미지를 만들지 않고 한도를 낮춰 같은 경로를 탄다 (한도의 2배 초과 → 오류)
    monkeypatch.setattr(Image, "MAX_IMAGE_PIXELS", 1000)
    original = encode(Image.new("RGB", (100, 100)))

    with pytest.raises(PermanentError) as error:
        render(original)
    assert error.value.error_type == "UNDECODABLE_IMAGE"
