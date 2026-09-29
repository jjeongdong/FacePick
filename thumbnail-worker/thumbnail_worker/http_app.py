"""HTTP 모드 입구: `uv run python -m thumbnail_worker --http`

백엔드가 업로드 완료 요청 안에서 동기로 부른다 (Kafka 방식과 비교 측정용).
처리 로직은 Kafka 모드와 같은 PhotoProcessor.process 를 쓴다.
"""

import logging
import threading
from collections.abc import Callable

import uvicorn
from fastapi import FastAPI
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from thumbnail_worker.config import Config
from thumbnail_worker.errors import PermanentError
from thumbnail_worker.messages import PreviewReady
from thumbnail_worker.photo_repository import PhotoRepository
from thumbnail_worker.processor import PhotoProcessor
from thumbnail_worker.storage import PhotoStorage

log = logging.getLogger(__name__)


class ProcessBody(BaseModel):
    photo_id: int = Field(alias="photoId", gt=0)


def create_app(process: Callable[[int], PreviewReady | None]) -> FastAPI:
    app = FastAPI()
    # Kafka 컨슈머처럼 한 번에 한 건만 처리해야 두 방식의 처리량이 같아져 전달 방식만 비교된다.
    # def 엔드포인트는 스레드 풀에서 돌아 요청이 동시에 들어오므로 잠근다.
    lock = threading.Lock()

    @app.post("/process")
    def process_photo(body: ProcessBody):
        try:
            with lock:
                result = process(body.photo_id)
        except PermanentError as e:
            log.error("영구 실패: photoId=%s type=%s %s", body.photo_id, e.error_type, e)
            return JSONResponse(
                status_code=422, content={"errorType": e.error_type, "message": str(e)}
            )
        if result is None:
            return JSONResponse(
                status_code=404,
                content={"errorType": "PHOTO_NOT_FOUND", "message": "photos 에 행이 없다"},
            )
        return {
            "photoId": result.photo_id,
            "albumId": result.album_id,
            "previewKey": result.preview_key,
            "width": result.width,
            "height": result.height,
        }

    return app


def serve(config: Config) -> None:
    processor = PhotoProcessor(
        PhotoRepository(config.database_url),
        PhotoStorage(
            config.s3_endpoint, config.s3_access_key, config.s3_secret_key, config.s3_bucket
        ),
    )
    log.info("썸네일 워커 HTTP 모드 시작: 포트 %s", config.http_port)
    uvicorn.run(create_app(processor.process), host="0.0.0.0", port=config.http_port)
