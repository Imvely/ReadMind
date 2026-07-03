package com.readmind.document.ai

import com.readmind.config.AiServiceProperties
import com.readmind.config.http11RequestFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import java.time.Duration

/** AI /ai/parse 응답 {chunkCount, language, pageCount} (명세서 §5.2). */
data class ParseResult(
    val chunkCount: Int,
    val language: String?,
    val pageCount: Int,
)

/**
 * AI 파싱 호출 포트. 구현 교체(동기 HTTP ↔ 큐) 가능하도록 인터페이스로 격리한다.
 * 호출부는 비동기(@Async) 스레드에서 실행한다.
 */
interface AiParseClient {
    fun parse(documentId: Long, storageKey: String, format: String): ParseResult
}

@Component
class RestClientAiParseClient(
    private val props: AiServiceProperties,
) : AiParseClient {

    private val client: RestClient = RestClient.builder()
        .baseUrl(props.baseUrl)
        .requestFactory(http11RequestFactory(readTimeout = Duration.ofSeconds(props.parseTimeoutSeconds)))
        .build()

    override fun parse(documentId: Long, storageKey: String, format: String): ParseResult {
        val body = mapOf(
            "documentId" to documentId,
            "storageKey" to storageKey,
            // AI 디스패처는 소문자 포맷 키를 기대한다.
            "format" to format.lowercase(),
        )
        // 콜드스타트/재배포 창의 일시 오류(5xx/429/타임아웃/연결)는 백오프 재시도 (§4.2).
        return retryTransient(
            maxAttempts = props.parseRetryMaxAttempts,
            initialBackoffMs = props.parseRetryInitialBackoffSeconds * 1000,
        ) {
            client.post()
                .uri("/ai/parse")
                .contentType(MediaType.APPLICATION_JSON)
                .apply {
                    if (props.serviceToken.isNotBlank()) header("X-Service-Token", props.serviceToken)
                }
                .body(body)
                .retrieve()
                .body<ParseResult>()
                ?: throw IllegalStateException("AI /ai/parse 응답이 비어 있습니다.")
        }
    }
}
