package com.readmind.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.readmind.auth.jwt.JwtTokenProvider
import com.readmind.common.ApiException
import com.readmind.common.ErrorCode
import com.readmind.config.JwtAuthFilter
import com.readmind.config.JwtAuthenticationEntryPoint
import com.readmind.config.JwtProperties
import com.readmind.config.SecurityConfig
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

/**
 * /documents/{id}/summarize·/qa HTTP 계약 + 권한/쿼터 슬라이스. AiService는 mock.
 * 실제 SecurityConfig/JwtAuthFilter를 임포트해 인증 경로(principal=userId)를 그대로 탄다.
 */
@WebMvcTest(AiController::class)
@Import(SecurityConfig::class, JwtAuthFilter::class, JwtAuthenticationEntryPoint::class, JwtTokenProvider::class)
@EnableConfigurationProperties(JwtProperties::class)
@TestPropertySource(properties = ["app.jwt.secret=test-secret-test-secret-test-secret-32+"])
class AiControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    @MockBean private lateinit var service: AiService

    private val userId = 10L
    private fun auth() = "Bearer " + tokenProvider.createAccessToken(userId, "a@b.com", "FREE")

    @Test
    fun `summarize 해피패스 - 200과 구조화 content, cached`() {
        whenever(service.summarize(eq(userId), eq(7L), any())).doReturn(
            SummarizeResponse(
                summaryId = 1L,
                scope = "DOCUMENT",
                style = "PAPER",
                content = objectMapper.readTree("""{"tldr":"요지"}"""),
                cached = false,
            ),
        )

        mockMvc.post("/documents/7/summarize") {
            header("Authorization", auth())
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("style" to "PAPER"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.success") { value(true) }
            jsonPath("$.data.content.tldr") { value("요지") }
            jsonPath("$.data.cached") { value(false) }
        }
    }

    @Test
    fun `qa 해피패스 - 200과 answer, sources(page,snippet)`() {
        whenever(service.qa(eq(userId), eq(7L), any())).doReturn(
            QaResponse(
                sessionId = 20L,
                answer = "답변",
                sources = listOf(QaSourceDto(page = 3, snippet = "근거문장")),
            ),
        )

        mockMvc.post("/documents/7/qa") {
            header("Authorization", auth())
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("question" to "이게 뭐야?"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.sessionId") { value(20) }
            jsonPath("$.data.answer") { value("답변") }
            jsonPath("$.data.sources[0].page") { value(3) }
            jsonPath("$.data.sources[0].snippet") { value("근거문장") }
        }
    }

    @Test
    fun `qa 검증 실패 - 빈 질문은 400 VALIDATION`() {
        mockMvc.post("/documents/7/qa") {
            header("Authorization", auth())
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("question" to "  "))
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value(ErrorCode.VALIDATION.name) }
        }
    }

    @Test
    fun `summarize 권한 실패 - 토큰 없으면 401`() {
        mockMvc.post("/documents/7/summarize") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("style" to "PAPER"))
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.error.code") { value(ErrorCode.UNAUTHORIZED.name) }
        }
    }

    @Test
    fun `qa 쿼터 초과 - 서비스가 QUOTA_EXCEEDED면 403`() {
        whenever(service.qa(eq(userId), eq(7L), any()))
            .thenThrow(ApiException(ErrorCode.QUOTA_EXCEEDED, "무료 사용량을 모두 소진했습니다."))

        mockMvc.post("/documents/7/qa") {
            header("Authorization", auth())
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("question" to "q"))
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.error.code") { value(ErrorCode.QUOTA_EXCEEDED.name) }
        }
    }
}
