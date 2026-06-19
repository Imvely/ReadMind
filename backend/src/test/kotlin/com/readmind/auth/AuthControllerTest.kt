package com.readmind.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.readmind.auth.jwt.JwtTokenProvider
import com.readmind.common.ApiException
import com.readmind.common.ErrorCode
import com.readmind.config.JwtAuthFilter
import com.readmind.config.JwtAuthenticationEntryPoint
import com.readmind.config.JwtProperties
import com.readmind.config.SecurityConfig
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.kotlin.any
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.get

/**
 * /auth HTTP 계약 + 검증 + 권한(401) 슬라이스 테스트.
 * AuthService는 mock — DB/Flyway 없이 컨트롤러·보안 필터 계층만 검증한다.
 * 실제 SecurityConfig/JwtAuthFilter/엔트리포인트를 임포트해 인증 경로를 그대로 탄다.
 */
@WebMvcTest(AuthController::class)
@Import(SecurityConfig::class, JwtAuthFilter::class, JwtAuthenticationEntryPoint::class, JwtTokenProvider::class)
@EnableConfigurationProperties(JwtProperties::class)
@TestPropertySource(properties = ["app.jwt.secret=test-secret-test-secret-test-secret-32+"])
class AuthControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var tokenProvider: JwtTokenProvider

    @MockBean private lateinit var authService: AuthService

    @Test
    fun `signup 해피패스 - 200과 success-data-userId 반환`() {
        given(authService.signup(any())).willReturn(SignupResponse(userId = 1L))

        mockMvc.post("/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf("email" to "a@b.com", "password" to "password123", "displayName" to "테스터"),
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.success") { value(true) }
            jsonPath("$.data.userId") { value(1) }
        }
    }

    @Test
    fun `signup 검증 실패 - 잘못된 이메일과 짧은 비밀번호는 400 VALIDATION`() {
        mockMvc.post("/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf("email" to "not-an-email", "password" to "short"),
            )
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.success") { value(false) }
            jsonPath("$.error.code") { value(ErrorCode.VALIDATION.name) }
        }
    }

    @Test
    fun `signup 이메일 중복 - 409 EMAIL_EXISTS`() {
        given(authService.signup(any()))
            .willThrow(ApiException(ErrorCode.EMAIL_EXISTS, "이미 가입된 이메일입니다."))

        mockMvc.post("/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf("email" to "dup@b.com", "password" to "password123"),
            )
        }.andExpect {
            status { isConflict() }
            jsonPath("$.success") { value(false) }
            jsonPath("$.error.code") { value(ErrorCode.EMAIL_EXISTS.name) }
        }
    }

    @Test
    fun `login 해피패스 - 토큰과 user 반환`() {
        given(authService.login(any())).willReturn(
            LoginResponse(
                accessToken = "access.jwt",
                refreshToken = "refresh.jwt",
                user = UserDto(id = 1L, email = "a@b.com", displayName = "테스터", tier = "FREE"),
            ),
        )

        mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf("email" to "a@b.com", "password" to "password123"),
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.accessToken") { value("access.jwt") }
            jsonPath("$.data.refreshToken") { value("refresh.jwt") }
            jsonPath("$.data.user.email") { value("a@b.com") }
        }
    }

    @Test
    fun `login 자격증명 실패 - 401 INVALID_CREDENTIALS`() {
        given(authService.login(any()))
            .willThrow(ApiException(ErrorCode.INVALID_CREDENTIALS, "이메일 또는 비밀번호가 올바르지 않습니다."))

        mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf("email" to "a@b.com", "password" to "wrongpassword"),
            )
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.error.code") { value(ErrorCode.INVALID_CREDENTIALS.name) }
        }
    }

    @Test
    fun `me 권한 실패 - 토큰 없이 호출하면 401 UNAUTHORIZED`() {
        mockMvc.get("/auth/me").andExpect {
            status { isUnauthorized() }
            jsonPath("$.success") { value(false) }
            jsonPath("$.error.code") { value(ErrorCode.UNAUTHORIZED.name) }
        }
    }

    @Test
    fun `me 권한 실패 - 손상된 Bearer 토큰도 401`() {
        mockMvc.get("/auth/me") {
            header("Authorization", "Bearer not.a.real.token")
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.error.code") { value(ErrorCode.UNAUTHORIZED.name) }
        }
    }

    @Test
    fun `me 해피패스 - 유효한 access 토큰이면 200과 내 정보`() {
        val token = tokenProvider.createAccessToken(userId = 1L, email = "a@b.com", tier = "FREE")
        given(authService.me(1L)).willReturn(
            MeResponse(
                id = 1L,
                email = "a@b.com",
                displayName = "테스터",
                tier = "FREE",
                quota = QuotaInfo(0, 10, 0, 20, 0, 10, 200),
            ),
        )

        mockMvc.get("/auth/me") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.id") { value(1) }
            jsonPath("$.data.email") { value("a@b.com") }
            jsonPath("$.data.quota.summaryLimit") { value(10) }
        }
    }

    @Test
    fun `refresh 해피패스 - 새 access 토큰 반환`() {
        given(authService.refresh(any())).willReturn(RefreshResponse(accessToken = "new.access.jwt"))

        mockMvc.post("/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("refreshToken" to "some.refresh.token"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.accessToken") { value("new.access.jwt") }
        }
    }
}
