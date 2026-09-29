"""측정용 합성 단체 사진 만들기: LFW 얼굴을 휴대폰 사진 크기(4032x3024) 캔버스에 배치한다.

실제 사진이 없을 때 쓴다. 인물 풀(기본 10명)에서 장마다 4~8명을 골라, 여러 장에 같은 사람이
반복해 나오게 한다 (여행 앨범처럼). 배경에 잡음을 넣어 파일 크기를 실제 사진(수 MB)에 가깝게 맞춘다.
LFW 는 face-worker 평가 스크립트가 받아 둔 캐시(face-worker/.cache/lfw_home)를 쓴다.

사용법 (Pillow·numpy 가 있는 환경): cd face-worker && uv run python ../loadtest/make_synthetic_photos.py 20
"""

import random
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

ROOT = Path(__file__).parent
LFW = ROOT.parent / "face-worker" / ".cache" / "lfw_home" / "lfw_funneled"
OUTPUT = ROOT / "source"
WIDTH, HEIGHT = 4032, 3024
PEOPLE = 10
MIN_IMAGES_PER_PERSON = 20


def person_pool(rng: random.Random) -> list[list[Path]]:
    candidates = [d for d in sorted(LFW.iterdir()) if d.is_dir()]
    candidates = [d for d in candidates if len(list(d.glob("*.jpg"))) >= MIN_IMAGES_PER_PERSON]
    return [sorted(d.glob("*.jpg")) for d in rng.sample(candidates, PEOPLE)]


def background(rng: random.Random) -> Image.Image:
    top = np.array([rng.randint(60, 200) for _ in range(3)], dtype=np.float32)
    bottom = np.array([rng.randint(30, 160) for _ in range(3)], dtype=np.float32)
    ramp = np.linspace(0, 1, HEIGHT, dtype=np.float32)[:, None, None]
    pixels = top * (1 - ramp) + bottom * ramp
    pixels = np.broadcast_to(pixels, (HEIGHT, WIDTH, 3)).copy()
    pixels += np.random.default_rng(rng.randint(0, 2**32 - 1)).normal(0, 12, pixels.shape)
    return Image.fromarray(np.clip(pixels, 0, 255).astype(np.uint8))


def main() -> None:
    count = int(sys.argv[1])
    rng = random.Random(42)
    pool = person_pool(rng)
    OUTPUT.mkdir(exist_ok=True)
    for index in range(count):
        canvas = background(rng)
        people = rng.sample(pool, rng.randint(4, 8))
        columns = len(people)
        cell = WIDTH // columns
        for column, images in enumerate(people):
            face = Image.open(rng.choice(images)).convert("RGB")
            size = min(cell - 40, rng.randint(520, 760))
            face = face.resize((size, size), Image.Resampling.LANCZOS).filter(ImageFilter.SHARPEN)
            x = column * cell + (cell - size) // 2
            y = rng.randint(HEIGHT // 5, HEIGHT - size - HEIGHT // 6)
            canvas.paste(face, (x, y))
        canvas.save(OUTPUT / f"synthetic_{index:02d}.jpg", "JPEG", quality=92)
    print(f"{count}장 생성: {OUTPUT}")


if __name__ == "__main__":
    main()
