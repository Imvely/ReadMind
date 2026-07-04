package com.readmind.sync

import com.fasterxml.jackson.databind.ObjectMapper
import com.readmind.auth.jwt.JwtTokenProvider
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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Instant

/** /sync HTTP 계약 + 권한(401) 슬라이스 (명세서 §4.5). SyncService는 mock. */
@WebMvcTest(SyncController::class)
@Import(SecurityConfig::class, JwtAuthFilter::class, JwtAuthenticationEntryPoint::class, JwtTokenProvider::class)
@EnableConfigurationProperties(JwtProperties::class)
@TestPropertySource(properties = ["app.jwt.secret=test-secret-test-secret-test-secret-32+"])
class SyncControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    @MockBean private lateinit var service: SyncService

    private val userId = 10L
    private fun auth() = "Bearer " + tokenProvider.createAccessToken(userId, "a@b.com", "FREE")

    @Test
    fun `changes 해피패스 - since 파싱과 cursor-changes 형태`() {
        whenever(service.changes(eq(userId), any())).doReturn(
            SyncChangesResponse(cursor = Instant.parse("2026-07-05T00:00:00Z"), changes = SyncChanges()),
        )

        mockMvc.get("/sync/changes?since=2026-07-01T00:00:00Z") {
            header("Authorization", auth())
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.cursor") { exists() }
            jsonPath("$.data.changes.highlights") { isArray() }
        }
    }

    @Test
    fun `push 해피패스 - applied와 conflicts 반환`() {
        whenever(service.push(eq(userId), any())).doReturn(
            SyncPushResponse(
                applied = listOf(AppliedDto("highlight", "local-1", 100, 1)),
                conflicts = emptyList(),
                cursor = Instant.parse("2026-07-05T00:00:00Z"),
            ),
        )

        mockMvc.post("/sync/push") {
            header("Authorization", auth())
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("changes" to emptyMap<String, Any>()))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.applied[0].clientId") { value("local-1") }
            jsonPath("$.data.applied[0].id") { value(100) }
        }
    }

    @Test
    fun `권한 실패 - 토큰 없으면 401`() {
        mockMvc.get("/sync/changes").andExpect {
            status { isUnauthorized() }
            jsonPath("$.error.code") { value(ErrorCode.UNAUTHORIZED.name) }
        }
    }
}
