import os
from dataclasses import dataclass

from dotenv import load_dotenv


@dataclass(frozen=True)
class MatchSettings:
    threshold: float
    knn_k: int
    min_size: int
    min_det_score: float


@dataclass(frozen=True)
class Config:
    kafka_bootstrap: str
    kafka_group_id: str
    topic_preview_ready: str
    topic_dlq: str
    s3_endpoint: str
    s3_access_key: str
    s3_secret_key: str
    s3_bucket: str
    database_url: str
    insightface_model: str
    match: MatchSettings
    http_port: int


def load_config() -> Config:
    load_dotenv()
    return Config(
        kafka_bootstrap=os.environ["KAFKA_BOOTSTRAP"],
        kafka_group_id=os.environ["KAFKA_GROUP_ID"],
        topic_preview_ready=os.environ["TOPIC_PREVIEW_READY"],
        topic_dlq=os.environ["TOPIC_DLQ"],
        s3_endpoint=os.environ["S3_ENDPOINT"],
        s3_access_key=os.environ["S3_ACCESS_KEY"],
        s3_secret_key=os.environ["S3_SECRET_KEY"],
        s3_bucket=os.environ["S3_BUCKET"],
        database_url=os.environ["DATABASE_URL"],
        insightface_model=os.environ["INSIGHTFACE_MODEL"],
        match=MatchSettings(
            threshold=float(os.environ["FACE_MATCH_THRESHOLD"]),
            knn_k=int(os.environ["FACE_KNN_K"]),
            min_size=int(os.environ["FACE_MIN_SIZE"]),
            min_det_score=float(os.environ["FACE_MIN_DET_SCORE"]),
        ),
        http_port=int(os.environ.get("HTTP_PORT", "8092")),
    )
