"""썸네일 워커 진입점: `uv run python -m thumbnail_worker` (Kafka) / `--http` (동기 방식 비교용)

흐름: photo.uploaded 수신 -> 원본 다운로드 -> 썸네일·미리보기 생성 -> 스토리지 저장
      -> photos 갱신 -> photo.preview_ready 발행
"""

import logging
import sys

from thumbnail_worker.config import load_config
from thumbnail_worker.http_app import serve
from thumbnail_worker.worker import run


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
