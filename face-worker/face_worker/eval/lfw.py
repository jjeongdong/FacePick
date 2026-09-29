import random
from pathlib import Path

MIN_IMAGES_PER_PERSON = 5


def load_samples(
    people: int, max_per_person: int, seed: int, cache_dir: Path
) -> list[tuple[str, bytes]]:
    """LFW(funneled) 원본 JPEG 를 (이름, 바이트) 목록으로 준다. 처음에는 약 230MB 를 받는다."""
    folder = _ensure_downloaded(cache_dir)
    candidates = sorted(
        person
        for person in folder.iterdir()
        if person.is_dir() and len(list(person.glob("*.jpg"))) >= MIN_IMAGES_PER_PERSON
    )
    rng = random.Random(seed)
    samples: list[tuple[str, bytes]] = []
    for person in sorted(rng.sample(candidates, people)):
        # 사진이 수백 장인 인물이 결과를 좌우하지 않게 인물당 장수를 자른다.
        for image in sorted(person.glob("*.jpg"))[:max_per_person]:
            samples.append((person.name, image.read_bytes()))
    return samples


def _ensure_downloaded(cache_dir: Path) -> Path:
    folder = cache_dir / "lfw_home" / "lfw_funneled"
    if not folder.is_dir():
        from sklearn.datasets import fetch_lfw_people

        # 배열 로딩은 쓰지 않는다(컬러 원본 전체는 수 GB). 다운로드·압축 해제만 시키려고
        # 사진이 가장 많은 몇 명만 작게 읽게 한다.
        # 원본 JPEG 는 cache_dir/lfw_home/lfw_funneled 에 남는다.
        fetch_lfw_people(data_home=cache_dir, min_faces_per_person=100, resize=0.2)
    if not folder.is_dir():
        raise RuntimeError(f"LFW 폴더를 찾지 못했다: {folder}")
    return folder
