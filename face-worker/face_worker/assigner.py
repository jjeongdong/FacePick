from collections import Counter
from dataclasses import dataclass
from typing import Protocol

import numpy as np

from face_worker.face import DetectedFace


@dataclass(frozen=True)
class Neighbor:
    person_id: int
    similarity: float


def assign(neighbors: list[Neighbor], used_person_ids: set[int], threshold: float) -> int | None:
    """kNN 투표로 인물을 고른다. None 이면 새 인물을 만들어야 한다."""
    # 한 사진에 같은 사람이 둘일 수 없으므로 이 사진에서 이미 쓴 인물은 후보에서 뺀다.
    candidates = [
        neighbor
        for neighbor in neighbors
        if neighbor.similarity >= threshold and neighbor.person_id not in used_person_ids
    ]
    if not candidates:
        return None
    votes = Counter(neighbor.person_id for neighbor in candidates)
    best_similarity: dict[int, float] = {}
    for neighbor in candidates:
        best_similarity[neighbor.person_id] = max(
            best_similarity.get(neighbor.person_id, -1.0), neighbor.similarity
        )
    return max(votes, key=lambda person_id: (votes[person_id], best_similarity[person_id]))


class AlbumFaces(Protocol):
    def nearest_faces(self, embedding: np.ndarray, k: int) -> list[Neighbor]: ...

    def create_person(self) -> int: ...

    def insert_face(self, photo_id: int, person_id: int, face: DetectedFace) -> int: ...

    def update_cover_if_better(self, person_id: int, face_id: int, det_score: float) -> None: ...


def assign_faces(
    album: AlbumFaces, photo_id: int, faces: list[DetectedFace], threshold: float, k: int
) -> list[int]:
    """사진 한 장의 얼굴을 배정한다. 처리 순서(det_score 내림차순)대로 person_id 목록을 돌려준다."""
    used_person_ids: set[int] = set()
    person_ids: list[int] = []
    # 선명한 얼굴이 먼저 자리를 잡아야 흐린 얼굴이 엉뚱한 인물을 만들거나 차지하지 않는다.
    for face in sorted(faces, key=lambda f: f.det_score, reverse=True):
        person_id = assign(album.nearest_faces(face.embedding, k), used_person_ids, threshold)
        if person_id is None:
            person_id = album.create_person()
        face_id = album.insert_face(photo_id, person_id, face)
        album.update_cover_if_better(person_id, face_id, face.det_score)
        used_person_ids.add(person_id)
        person_ids.append(person_id)
    return person_ids
