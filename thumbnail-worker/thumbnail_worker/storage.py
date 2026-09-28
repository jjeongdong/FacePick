import boto3
from botocore.config import Config as BotoConfig

from thumbnail_worker.errors import PermanentError


class PhotoStorage:
    def __init__(self, endpoint: str, access_key: str, secret_key: str, bucket: str):
        self._bucket = bucket
        self._s3 = boto3.client(
            "s3",
            endpoint_url=endpoint,
            aws_access_key_id=access_key,
            aws_secret_access_key=secret_key,
            region_name="us-east-1",
            config=BotoConfig(
                # SeaweedFS 는 가상 호스트 방식 버킷 주소를 받지 않는다 (백엔드도 path-style).
                s3={"addressing_style": "path"},
                # 최신 boto3 가 기본으로 붙이는 CRC 체크섬을 S3 호환 스토리지가 모를 수 있다.
                request_checksum_calculation="when_required",
                response_checksum_validation="when_required",
            ),
        )

    def download(self, key: str) -> bytes:
        try:
            return self._s3.get_object(Bucket=self._bucket, Key=key)["Body"].read()
        except self._s3.exceptions.NoSuchKey as e:
            raise PermanentError("ORIGINAL_MISSING", f"원본이 스토리지에 없다: {key}") from e

    def upload_jpeg(self, key: str, data: bytes) -> None:
        self._s3.put_object(Bucket=self._bucket, Key=key, Body=data, ContentType="image/jpeg")
