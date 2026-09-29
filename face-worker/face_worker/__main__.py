"""얼굴 분석 워커 진입점: `uv run python -m face_worker` (Kafka) / `--http` (동기 방식 비교용)

흐름: photo.preview_ready 수신 -> 미리보기 다운로드 -> 얼굴 검출·임베딩
      -> 앨범 안에서 kNN 투표로 인물 배정(없으면 새 인물) -> faces·persons·face_analyses 저장
"""

import logging
import sys

from face_worker.config import load_config
from face_worker.http_app import serve
from face_worker.worker import run


def main() -> None:
    logging.basicConfig(
        level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s - %(message)s"
    )
    config = load_config()
    if "--http" in sys.argv[1:]:
        serve(config)
    else:
        run(config)


if __name__ == "__main__":
    main()
