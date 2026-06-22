package com.readmind.document.storage

import com.readmind.config.S3Properties
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI
import kotlin.test.assertTrue

/**
 * 실제 AWS SDK presigner로 서명을 검증한다(서명은 로컬 연산 — 라이브 서버 불필요).
 * MinIO path-style 가정: URL에 버킷/키 경로 + SigV4 서명 쿼리가 들어가야 한다.
 */
class DocumentStorageTest {

    private val props = S3Properties(
        endpoint = "http://localhost:9000",
        bucket = "readmind",
        accessKey = "minioadmin",
        secretKey = "test-secret",
        region = "us-east-1",
        pathStyle = true,
        presignTtlSeconds = 900,
    )

    private val presigner: S3Presigner = S3Presigner.builder()
        .endpointOverride(URI.create(props.endpoint))
        .region(Region.of(props.region))
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(props.accessKey, props.secretKey)))
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        .build()

    private val storage = DocumentStorage(presigner, props)

    @AfterEach
    fun tearDown() = presigner.close()

    @Test
    fun `newStorageKey는 사용자 네임스페이스 + 소문자 확장자`() {
        val key = storage.newStorageKey(userId = 42L, format = "PDF")
        assertTrue(key.startsWith("users/42/"), "사용자 네임스페이스로 격리되어야 함: $key")
        assertTrue(key.endsWith(".pdf"), "확장자는 소문자여야 함: $key")
    }

    @Test
    fun `presignPut은 path-style 경로 + SigV4 서명 쿼리를 포함한다`() {
        val url = storage.presignPut("users/42/abc.pdf")
        assertTrue(url.contains("/readmind/users/42/abc.pdf"), "path-style 버킷/키 경로: $url")
        assertTrue(url.contains("X-Amz-Signature="), "SigV4 서명 포함: $url")
        assertTrue(url.contains("X-Amz-Expires="), "만료 포함: $url")
    }

    @Test
    fun `presignGet은 동일 객체에 대한 서명 URL을 만든다`() {
        val url = storage.presignGet("users/42/abc.pdf")
        assertTrue(url.contains("/readmind/users/42/abc.pdf"), "path-style 경로: $url")
        assertTrue(url.contains("X-Amz-Signature="), "SigV4 서명 포함: $url")
    }
}
