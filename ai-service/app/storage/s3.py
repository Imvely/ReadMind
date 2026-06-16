"""S3 호환 스토리지 어댑터(boto3) — MinIO/실 S3 공용.

endpoint_url로 MinIO를 가리키고, region/자격증명은 env(명세서 §10)로 주입한다.
특정 클라우드에 묶지 않는다 — S3 호환 API만 사용.
"""

from __future__ import annotations

import boto3
from botocore.config import Config


class S3DownloadError(RuntimeError):
    """객체 다운로드 실패."""


class S3Storage:
    def __init__(
        self,
        *,
        endpoint_url: str,
        bucket: str,
        access_key: str,
        secret_key: str,
        region: str = "us-east-1",
    ) -> None:
        self._bucket = bucket
        self._client = boto3.client(
            "s3",
            endpoint_url=endpoint_url,
            aws_access_key_id=access_key,
            aws_secret_access_key=secret_key,
            region_name=region,
            config=Config(signature_version="s3v4"),
        )

    def download(self, storage_key: str) -> bytes:
        try:
            resp = self._client.get_object(Bucket=self._bucket, Key=storage_key)
            return resp["Body"].read()
        except Exception as exc:  # boto3 ClientError 등
            raise S3DownloadError(
                f"S3 다운로드 실패: bucket={self._bucket} key={storage_key} {exc!r}"
            ) from exc
