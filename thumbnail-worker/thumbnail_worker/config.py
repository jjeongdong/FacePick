import os
from dataclasses import dataclass

from dotenv import load_dotenv


@dataclass(frozen=True)
class Config:
    kafka_bootstrap: str
    kafka_group_id: str
    topic_photo_uploaded: str
    topic_preview_ready: str
    topic_dlq: str
    s3_endpoint: str
    s3_access_key: str
    s3_secret_key: str
    s3_bucket: str
    database_url: str
    http_port: int


def load_config() -> Config:
    load_dotenv()
    return Config(
        kafka_bootstrap=os.environ["KAFKA_BOOTSTRAP"],
        kafka_group_id=os.environ["KAFKA_GROUP_ID"],
        topic_photo_uploaded=os.environ["TOPIC_PHOTO_UPLOADED"],
        topic_preview_ready=os.environ["TOPIC_PREVIEW_READY"],
        topic_dlq=os.environ["TOPIC_DLQ"],
        s3_endpoint=os.environ["S3_ENDPOINT"],
        s3_access_key=os.environ["S3_ACCESS_KEY"],
        s3_secret_key=os.environ["S3_SECRET_KEY"],
        s3_bucket=os.environ["S3_BUCKET"],
        database_url=os.environ["DATABASE_URL"],
        http_port=int(os.environ.get("HTTP_PORT", "8091")),
    )
