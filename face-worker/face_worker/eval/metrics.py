from collections import Counter
from dataclasses import dataclass


@dataclass(frozen=True)
class ClusterScore:
    precision: float  # 같은 인물로 묶인 얼굴 쌍 중 실제 같은 사람 비율
    recall: float  # 실제 같은 사람인 얼굴 쌍 중 같은 인물로 묶인 비율
    persons_per_identity: float  # 한 사람이 평균 몇 개 인물로 쪼개졌는지 (1.0 이 최선)


def _pairs(count: int) -> int:
    return count * (count - 1) // 2


def score(true_labels: list[str], predicted: list[int]) -> ClusterScore:
    cells = Counter(zip(true_labels, predicted, strict=True))
    together_and_same = sum(_pairs(n) for n in cells.values())
    together = sum(_pairs(n) for n in Counter(predicted).values())
    same = sum(_pairs(n) for n in Counter(true_labels).values())
    return ClusterScore(
        precision=together_and_same / together if together else 1.0,
        recall=together_and_same / same if same else 1.0,
        persons_per_identity=len(cells) / len(set(true_labels)),
    )
