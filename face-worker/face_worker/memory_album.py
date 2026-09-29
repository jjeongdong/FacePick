import numpy as np

from face_worker.assigner import Neighbor
from face_worker.face import DetectedFace


class InMemoryAlbum:
    """DB 없이 AlbumFaces 를 흉내 낸다.

    평가 스크립트와 테스트가 운영과 같은 배정 코드를 돌리기 위해 쓴다.
    """

    def __init__(self):
        self._embeddings: list[np.ndarray] = []
        self._person_ids: list[int] = []
        self._next_person_id = 1
        self.covers: dict[int, tuple[int, float]] = {}

    def nearest_faces(self, embedding: np.ndarray, k: int) -> list[Neighbor]:
        if not self._embeddings:
            return []
        similarities = np.stack(self._embeddings) @ embedding
        top = np.argsort(-similarities)[:k]
        return [Neighbor(self._person_ids[i], float(similarities[i])) for i in top]

    def create_person(self) -> int:
        person_id = self._next_person_id
        self._next_person_id += 1
        return person_id

    def insert_face(self, photo_id: int, person_id: int, face: DetectedFace) -> int:
        self._embeddings.append(face.embedding)
        self._person_ids.append(person_id)
        return len(self._embeddings)

    def update_cover_if_better(self, person_id: int, face_id: int, det_score: float) -> None:
        current = self.covers.get(person_id)
        if current is None or current[1] < det_score:
            self.covers[person_id] = (face_id, det_score)
