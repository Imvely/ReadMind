package com.readmind.document

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
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/**
 * /documents HTTP 계약 + 소유권/권한 슬라이스. DocumentService는 mock.
 * 실제 SecurityConfig/JwtAuthFilter를 임포트해 인증 경로(principal=userId)를 그대로 탄다.
 */
@WebMvcTest(DocumentController::class)
@Import(SecurityConfig::class, JwtAuthFilter::class, JwtAuthenticationEntryPoint::class, JwtTokenProvider::class)
@EnableConfigurationProperties(JwtProperties::class)
@TestPropertySource(properties = ["app.jwt.secret=test-secret-test-secret-test-secret-32+"])
class DocumentControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    @MockBean private lateinit var service: DocumentService

    private val userId = 10L
    private fun auth() = "Bearer " + tokenProvider.createAccessToken(userId, "a@b.com", "FREE")

    @Test
    fun `create 해피패스 - 200과 documentId, uploadUrl`() {
        whenever(service.create(eq(userId), any())).doReturn(CreateDocumentResponse(7L, "https://put"))

        mockMvc.post("/documents") {
            header("Authorization", auth())
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf("title" to "논문", "format" to "pdf", "fileSize" to 12345),
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.success") { value(true) }
            jsonPath("$.data.documentId") { value(7) }
            jsonPath("$.data.uploadUrl") { value("https://put") }
        }
    }

    @Test
    fun `create 검증 실패 - 빈 제목, 음수 크기는 400 VALIDATION`() {
        mockMvc.post("/documents") {
            header("Authorization", auth())
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf("title" to "", "format" to "pdf", "fileSize" to -5),
            )
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value(ErrorCode.VALIDATION.name) }
        }
    }

    @Test
    fun `create 권한 실패 - 토큰 없으면 401`() {
        mockMvc.post("/documents") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf("title" to "t", "format" to "pdf", "fileSize" to 1),
            )
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.error.code") { value(ErrorCode.UNAUTHORIZED.name) }
        }
    }

    @Test
    fun `complete 해피패스 - 200과 PARSING`() {
        whenever(service.complete(userId, 7L)).doReturn(CompleteResponse(7L, ParseStatus.PARSING))

        mockMvc.post("/documents/7/complete") {
            header("Authorization", auth())
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.parseStatus") { value("PARSING") }
        }
    }

    @Test
    fun `get 해피패스 - 200과 문서 메타`() {
        whenever(service.get(userId, 7L)).doReturn(
            DocumentDto(7L, "논문", "PDF", 12345, 8, "ko", ParseStatus.READY, null),
        )

        mockMvc.get("/documents/7") {
            header("Authorization", auth())
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.id") { value(7) }
            jsonPath("$.data.parseStatus") { value("READY") }
        }
    }

    @Test
    fun `get 소유권 실패 - 서비스가 NOT_FOUND면 404`() {
        whenever(service.get(userId, 99L))
            .thenThrow(ApiException(ErrorCode.NOT_FOUND, "문서를 찾을 수 없습니다."))

        mockMvc.get("/documents/99") {
            header("Authorization", auth())
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value(ErrorCode.NOT_FOUND.name) }
        }
    }

    @Test
    fun `list 해피패스 - items, totalElements, hasNext`() {
        whenever(service.list(eq(userId), any())).doReturn(
            DocumentListResponse(
                items = listOf(DocumentDto(7L, "논문", "PDF", 1, null, null, ParseStatus.PENDING, null)),
                totalElements = 1,
                hasNext = false,
            ),
        )

        mockMvc.get("/documents") {
            header("Authorization", auth())
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.items[0].id") { value(7) }
            jsonPath("$.data.totalElements") { value(1) }
            jsonPath("$.data.hasNext") { value(false) }
        }
    }

    @Test
    fun `content 해피패스 - presigned GET URL`() {
        whenever(service.content(userId, 7L)).doReturn(DocumentContentResponse("https://get"))

        mockMvc.get("/documents/7/content") {
            header("Authorization", auth())
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.url") { value("https://get") }
        }
    }

    @Test
    fun `delete 해피패스 - 200 success`() {
        mockMvc.delete("/documents/7") {
            header("Authorization", auth())
        }.andExpect {
            status { isOk() }
            jsonPath("$.success") { value(true) }
        }
    }
}
