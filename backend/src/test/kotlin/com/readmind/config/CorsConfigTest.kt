package com.readmind.config

import com.readmind.auth.AuthController
import com.readmind.auth.AuthService
import com.readmind.auth.jwt.JwtTokenProvider
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options

/**
 * CORS 계약 테스트 — 정적 웹 배포(§8 P0 베타) 대비.
 * 허용 오리진의 preflight 는 통과하고, 미허용 오리진은 거부되는지 검증한다.
 */
@WebMvcTest(AuthController::class)
@Import(SecurityConfig::class, JwtAuthFilter::class, JwtAuthenticationEntryPoint::class, JwtTokenProvider::class)
@EnableConfigurationProperties(JwtProperties::class)
@TestPropertySource(
    properties = [
        "app.jwt.secret=test-secret-test-secret-test-secret-32+",
        "app.cors.allowed-origins=https://readmind-web.example.com",
    ],
)
class CorsConfigTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @MockBean private lateinit var authService: AuthService

    @Test
    fun `허용 오리진의 preflight 는 Allow-Origin 헤더와 함께 200`() {
        mockMvc.perform(
            options("/auth/login")
                .header(HttpHeaders.ORIGIN, "https://readmind-web.example.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"),
        )
            .andExpect { result ->
                assert(result.response.status == 200) { "status=${result.response.status}" }
                val allow = result.response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)
                assert(allow == "https://readmind-web.example.com") { "allowOrigin=$allow" }
            }
    }

    @Test
    fun `미허용 오리진의 preflight 는 거부된다`() {
        mockMvc.perform(
            options("/auth/login")
                .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"),
        )
            .andExpect { result ->
                assert(result.response.status == 403) { "status=${result.response.status}" }
                val allow = result.response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)
                assert(allow == null) { "미허용 오리진에 Allow-Origin 이 내려감: $allow" }
            }
    }
}
