import pytest

from face_worker.eval.metrics import score


def test_perfect_clustering():
    result = score(["a", "a", "b", "b"], [1, 1, 2, 2])
    assert (result.precision, result.recall, result.persons_per_identity) == (1.0, 1.0, 1.0)


def test_one_person_split_in_two_lowers_recall_only():
    # a 3장이 인물 1(2장)·인물 2(1장)로 쪼개짐: a 쌍 3개 중 1개만 같이 묶임
    result = score(["a", "a", "a", "b", "b"], [1, 1, 2, 3, 3])
    assert result.precision == 1.0
    assert result.recall == pytest.approx(2 / 4)
    assert result.persons_per_identity == pytest.approx(3 / 2)


def test_two_people_merged_lowers_precision_only():
    result = score(["a", "a", "b", "b"], [1, 1, 1, 1])
    assert result.precision == pytest.approx(2 / 6)
    assert result.recall == 1.0


def test_all_singletons_have_no_pairs():
    result = score(["a", "b"], [1, 2])
    assert (result.precision, result.recall) == (1.0, 1.0)
