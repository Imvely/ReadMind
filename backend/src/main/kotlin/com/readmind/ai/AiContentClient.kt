package com.readmind.ai

import com.fasterxml.jackson.databind.JsonNode
import com.readmind.common.ApiException
import com.readmind.common.ErrorCode
import com.readmind.config.AiServiceProperties
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.body

/** AI /ai/qa 응답의 근거 한 건 (명세서 §5.4). */
data class AiSource(
    val chunkIndex: Int,
    val pageNo: Int?,
    val snippet: String,
)

/** AI /ai/qa 응답 {answer, sources[]} (명세서 §5.4). */
data class AiQaResult(
    val answer: String,
    val sources: List<AiSource> = emptyList(),
)

/** 대화 이력 한 줄 (role=user|assistant). */
data class AiQaTurn(val role: String, val content: String)

/**
 * 요약/QA AI 호출 포트 (명세서 §5.3/§5.4). 동기 HTTP ↔ 큐 교체 가능하도록 인터페이스로 격리.
 * AI 서비스 오류는 ErrorCode.INTERNAL(502 상당)로 매핑하지 않고 도메인 예외로 위임 — 여기선
 * 호출 실패를 ApiException(INTERNAL)로 변환해 캐시 저장/쿼터 차감이 일어나지 않게 한다.
 */
interface AiContentClient {
    fun summarize(documentId: Long, style: String): JsonNode
    fun qa(documentId: Long, question: String, history: List<AiQaTurn>): AiQaResult
}

@Component
class RestClientAiContentClient(
    private val props: AiServiceProperties,
) : AiContentClient {

    private val client: RestClient = RestClient.builder()
        .baseUrl(props.baseUrl)
        .build()

    override fun summarize(documentId: Long, style: String): JsonNode {
        val body = mapOf("documentId" to documentId, "style" to style)
        return post("/ai/summarize", body, JsonNode::class.java)
    }

    override fun qa(documentId: Long, question: String, history: List<AiQaTurn>): AiQaResult {
        val body = mapOf(
            "documentId" to documentId,
            "question" to question,
            "history" to history.map { mapOf("role" to it.role, "content" to it.content) },
        )
        return post("/ai/qa", body, AiQaResult::class.java)
    }

    private fun <T> post(uri: String, body: Map<String, Any?>, type: Class<T>): T =
        try {
            client.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .apply {
                    if (props.serviceToken.isNotBlank()) header("X-Service-Token", props.serviceToken)
                }
                .body(body)
                .retrieve()
                .body(type)
                ?: throw ApiException(ErrorCode.INTERNAL, "AI 서비스 응답이 비어 있습니다.")
        } catch (ex: RestClientException) {
            throw ApiException(ErrorCode.INTERNAL, "AI 서비스 호출에 실패했습니다: ${ex.message}")
        }
}
