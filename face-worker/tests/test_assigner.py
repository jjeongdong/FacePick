from face_worker.assigner import Neighbor, assign, assign_faces
from face_worker.memory_album import InMemoryAlbum

THRESHOLD = 0.5


def test_no_neighbors_means_new_person():
    assert assign([], set(), THRESHOLD) is None


def test_all_neighbors_below_threshold_means_new_person():
    assert assign([Neighbor(1, 0.49), Neighbor(2, 0.3)], set(), THRESHOLD) is None


def test_threshold_is_inclusive():
    assert assign([Neighbor(1, 0.5)], set(), THRESHOLD) == 1


def test_majority_wins_over_single_closest():
    neighbors = [Neighbor(1, 0.95), Neighbor(2, 0.7), Neighbor(2, 0.65)]
    assert assign(neighbors, set(), THRESHOLD) == 2


def test_tie_goes_to_person_with_most_similar_neighbor():
    neighbors = [Neighbor(1, 0.7), Neighbor(2, 0.9), Neighbor(1, 0.6), Neighbor(2, 0.55)]
    assert assign(neighbors, set(), THRESHOLD) == 2


def test_person_already_in_this_photo_is_not_a_candidate():
    neighbors = [Neighbor(1, 0.95), Neighbor(1, 0.9), Neighbor(2, 0.6)]
    assert assign(neighbors, {1}, THRESHOLD) == 2


def test_only_used_persons_above_threshold_means_new_person():
    assert assign([Neighbor(1, 0.95)], {1}, THRESHOLD) is None


def test_same_person_across_photos_gets_same_id(unit_face):
    album = InMemoryAlbum()

    first = assign_faces(album, 1, [unit_face(1, 0)], THRESHOLD, 5)
    second = assign_faces(album, 2, [unit_face(1, 0.1)], THRESHOLD, 5)

    assert first == second


def test_different_people_get_different_ids(unit_face):
    album = InMemoryAlbum()

    first = assign_faces(album, 1, [unit_face(1, 0)], THRESHOLD, 5)
    second = assign_faces(album, 2, [unit_face(0, 1)], THRESHOLD, 5)

    assert first != second


def test_two_look_alike_faces_in_one_photo_become_two_persons(unit_face):
    # 거울·포스터처럼 한 사진에 같은 사람으로 보이는 얼굴이 둘이어도 한 인물에 몰지 않는다.
    album = InMemoryAlbum()

    person_ids = assign_faces(album, 1, [unit_face(1, 0), unit_face(1, 0.05)], THRESHOLD, 5)

    assert len(set(person_ids)) == 2


def test_faces_in_photo_are_processed_by_det_score(unit_face):
    album = InMemoryAlbum()
    blurry = unit_face(1, 0, det_score=0.7)
    sharp = unit_face(0, 1, det_score=0.95)

    person_ids = assign_faces(album, 1, [blurry, sharp], THRESHOLD, 5)

    # 선명한 얼굴이 먼저 처리돼 인물 1이 된다.
    assert person_ids == [1, 2]
    assert album.covers[1] == (1, 0.95)


def test_cover_moves_to_better_face(unit_face):
    album = InMemoryAlbum()
    assign_faces(album, 1, [unit_face(1, 0, det_score=0.7)], THRESHOLD, 5)

    assign_faces(album, 2, [unit_face(1, 0.1, det_score=0.9)], THRESHOLD, 5)
    assign_faces(album, 3, [unit_face(1, 0.2, det_score=0.8)], THRESHOLD, 5)

    assert album.covers[1] == (2, 0.9)
