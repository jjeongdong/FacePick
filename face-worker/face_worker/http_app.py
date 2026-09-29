"""HTTP 모드 입구: `uv run python -m face_worker --http`

백엔드가 업로드 완료 요청 안에서 thumbnail 다음으로 동기로 부른다 (Kafka 방식과 비교 측정용).
처리 로직은 Kafka 모드와 같은 FaceProcessor.analyze 를 쓴다.
"""

import logging
import threading
from collections.abc import Callable

import uvicorn
from fastapi import FastAPI
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from face_worker.config import Config
from face_worker.detector import FaceDetector
from face_worker.errors import PermanentError
from face_worker.face_repository import FaceRepository
from face_worker.messages import PreviewReady
from face_worker.processor import AnalysisResult, FaceProcessor
from face_worker.storage import PreviewStorage

log = logging.getLogger(__name__)


class AnalyzeBody(BaseModel):
    photo_id: int = Field(alias="photoId", gt=0)
    album_id: int = Field(alias="albumId", gt=0)
    preview_key: str = Field(alias="previewKey", min_length=1)


def create_app(analyze: Callable[[PreviewReady], AnalysisResult]) -> FastAPI:
    app = FastAPI()
    # Kafka 컨슈머처럼 한 번에 한 건만 처리해야 두 방식의 처리량이 같아져 전달 방식만 비교된다.
    # def 엔드포인트는 스레드 풀에서 돌아 요청이 동시에 들어오므로 잠근다.
    lock = threading.Lock()

    @app.post("/analyze")
    def analyze_photo(body: AnalyzeBody):
        message = PreviewReady(
            photo_id=body.photo_id, album_id=body.album_id, preview_key=body.preview_key
        )
        try:
            with lock:
                result = analyze(message)
        except PermanentError as e:
            log.error("영구 실패: photoId=%s type=%s %s", body.photo_id, e.error_type, e)
            return JSONResponse(
                status_code=422, content={"errorType": e.error_type, "message": str(e)}
            )
        return {"photoId": body.photo_id, "result": result.status, "faceCount": result.face_count}

    return app


def serve(config: Config) -> None:
    # 요청마다 불러오면 수 초씩 걸려 비교가 불공정해지므로 시작할 때 한 번만 불러온다.
    log.info("얼굴 모델을 불러온다: %s", config.insightface_model)
    processor = FaceProcessor(
        FaceRepository(config.database_url),
        PreviewStorage(
            config.s3_endpoint, config.s3_access_key, config.s3_secret_key, config.s3_bucket
        ),
        FaceDetector(config.insightface_model),
        config.match,
    )
    log.info("얼굴 분석 워커 HTTP 모드 시작: 포트 %s", config.http_port)
    uvicorn.run(create_app(processor.analyze), host="0.0.0.0", port=config.http_port)
