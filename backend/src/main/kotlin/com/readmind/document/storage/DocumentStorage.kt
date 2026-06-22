package com.readmind.document.storage

import com.readmind.config.S3Properties
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.time.Duration
import java.util.UUID

/**
 * 문서 원본 객체 스토리지 어댑터(S3/MinIO). presigned URL만 다루고 바이트는 클라이언트↔S3 직접 전송.
 * storageKey는 사용자별 네임스페이스로 격리한다.
 */
@Component
class DocumentStorage(
    private val presigner: S3Presigner,
    private val props: S3Properties,
) {
    private val ttl = Duration.ofSeconds(props.presignTtlSeconds)

    /** users/{userId}/{uuid}.{ext} — 소유자 네임스페이스 + 충돌없는 키. */
    fun newStorageKey(userId: Long, format: String): String =
        "users/$userId/${UUID.randomUUID()}.${format.lowercase()}"

    /** 업로드용 presigned PUT URL. */
    fun presignPut(storageKey: String): String {
        val put = PutObjectRequest.builder()
            .bucket(props.bucket)
            .key(storageKey)
            .build()
        val req = PutObjectPresignRequest.builder()
            .signatureDuration(ttl)
            .putObjectRequest(put)
            .build()
        return presigner.presignPutObject(req).url().toString()
    }

    /** 렌더용 presigned GET URL(권한 확인은 호출부 책임). */
    fun presignGet(storageKey: String): String {
        val get = GetObjectRequest.builder()
            .bucket(props.bucket)
            .key(storageKey)
            .build()
        val req = GetObjectPresignRequest.builder()
            .signatureDuration(ttl)
            .getObjectRequest(get)
            .build()
        return presigner.presignGetObject(req).url().toString()
    }
}
