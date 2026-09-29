from face_worker.face import clamp_bbox


def test_rounds_bbox_inside_image():
    assert clamp_bbox((10.4, 20.6, 110.5, 220.2), 400, 300) == (10, 21, 110, 220)


def test_clamps_bbox_crossing_image_edges():
    # 사진 가장자리에 걸린 얼굴은 검출기가 음수·이미지 밖 좌표를 준다.
    assert clamp_bbox((-15.0, -3.2, 420.0, 310.9), 400, 300) == (0, 0, 400, 300)


def test_short_side(unit_face):
    face = unit_face(1, size=64)
    assert face.short_side() == 64
