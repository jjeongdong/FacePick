"""임계값 평가: `uv run python -m face_worker.eval --thresholds 0.30,0.35,0.40`

LFW 사진을 운영과 같은 검출·품질 필터·배정 코드(InMemoryAlbum 사용)에 무작위 순서로 넣고
얼굴 쌍 기준 정밀도·재현율을 임계값별로 출력한다.
"""

import argparse
import logging
import random
import statistics
from pathlib import Path

from face_worker.assigner import assign_faces
from face_worker.config import load_config
from face_worker.detector import FaceDetector
from face_worker.eval.lfw import load_samples
from face_worker.eval.metrics import ClusterScore, score
from face_worker.face import DetectedFace
from face_worker.memory_album import InMemoryAlbum
from face_worker.quality import filter_faces

CACHE_DIR = Path(__file__).resolve().parents[2] / ".cache"
TARGET_PRECISION = 0.95


def main() -> None:
    logging.basicConfig(level=logging.WARNING)
    parser = argparse.ArgumentParser()
    parser.add_argument("--thresholds", default="0.30,0.35,0.40,0.45,0.50,0.55,0.60")
    parser.add_argument("--people", type=int, default=30)
    parser.add_argument("--max-per-person", type=int, default=20)
    parser.add_argument("--seeds", default="1,2,3")
    args = parser.parse_args()
    thresholds = [float(value) for value in args.thresholds.split(",")]
    seeds = [int(value) for value in args.seeds.split(",")]

    config = load_config()
    detector = FaceDetector(config.insightface_model)
    samples = load_samples(args.people, args.max_per_person, seed=0, cache_dir=CACHE_DIR)

    faces: list[tuple[str, DetectedFace]] = []
    for label, image in samples:
        detected = filter_faces(
            detector.detect(image), config.match.min_size, config.match.min_det_score
        )
        if detected:
            # LFW 사진에는 뒤에 다른 사람이 찍힌 경우가 있어 가장 큰 얼굴을 그 사람으로 본다.
            faces.append((label, max(detected, key=lambda face: face.short_side())))
    print(f"사진 {len(samples)}장 중 얼굴 {len(faces)}개 사용 (인물 {args.people}명)")

    print(f"{'T':>5} {'정밀도':>8} {'재현율':>8} {'인물/사람':>9}")
    best: tuple[float, float] | None = None
    for threshold in thresholds:
        results = [_run_once(faces, threshold, config.match.knn_k, seed) for seed in seeds]
        precision = statistics.mean(r.precision for r in results)
        recall = statistics.mean(r.recall for r in results)
        split = statistics.mean(r.persons_per_identity for r in results)
        print(f"{threshold:>5.2f} {precision:>8.3f} {recall:>8.3f} {split:>9.2f}")
        if precision >= TARGET_PRECISION and (best is None or recall > best[1]):
            best = (threshold, recall)
    if best is None:
        print(f"정밀도 {TARGET_PRECISION} 이상인 임계값이 없다. 더 높은 값을 넣어 다시 돌린다.")
    else:
        print(f"추천 FACE_MATCH_THRESHOLD={best[0]:.2f} (재현율 {best[1]:.3f})")


def _run_once(
    faces: list[tuple[str, DetectedFace]], threshold: float, k: int, seed: int
) -> ClusterScore:
    order = list(range(len(faces)))
    random.Random(seed).shuffle(order)
    album = InMemoryAlbum()
    labels: list[str] = []
    predicted: list[int] = []
    for photo_id, index in enumerate(order, start=1):
        label, face = faces[index]
        [person_id] = assign_faces(album, photo_id, [face], threshold, k)
        labels.append(label)
        predicted.append(person_id)
    return score(labels, predicted)


if __name__ == "__main__":
    main()
