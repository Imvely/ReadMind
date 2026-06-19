package com.readmind.auth.jwt

import com.readmind.config.JwtProperties
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** JwtTokenProvider 단위 테스트 — DB/스프링 컨텍스트 없이 토큰 발급·검증 계약만. */
class JwtTokenProviderTest {

    private val secret = "test-secret-test-secret-test-secret-32+"
    private val provider = JwtTokenProvider(
        JwtProperties(secret = secret, accessTtlSeconds = 900, refreshTtlSeconds = 1_209_600),
    )

    @Test
    fun `access 토큰을 발급하고 검증하면 userId를 돌려준다`() {
        val token = provider.createAccessToken(userId = 42L, email = "a@b.com", tier = "FREE")
        assertEquals(42L, provider.parseAccessSubject(token))
    }

    @Test
    fun `refresh 토큰을 발급하고 검증하면 userId를 돌려준다`() {
        val token = provider.createRefreshToken(userId = 7L)
        assertEquals(7L, provider.parseRefreshSubject(token))
    }

    @Test
    fun `access 토큰을 refresh로 검증하면 거부한다(type 분리)`() {
        val access = provider.createAccessToken(userId = 1L, email = "a@b.com", tier = "FREE")
        assertNull(provider.parseRefreshSubject(access))
    }

    @Test
    fun `refresh 토큰을 access로 검증하면 거부한다(type 분리)`() {
        val refresh = provider.createRefreshToken(userId = 1L)
        assertNull(provider.parseAccessSubject(refresh))
    }

    @Test
    fun `손상된 토큰은 null을 반환한다`() {
        assertNull(provider.parseAccessSubject("not.a.jwt"))
        assertNull(provider.parseAccessSubject(""))
    }

    @Test
    fun `다른 키로 서명된 토큰은 거부한다(위조 방지)`() {
        val other = JwtTokenProvider(
            JwtProperties(secret = "another-secret-another-secret-32bytes!!", accessTtlSeconds = 900),
        )
        val forged = other.createAccessToken(userId = 99L, email = "x@y.com", tier = "FREE")
        assertNull(provider.parseAccessSubject(forged))
    }

    @Test
    fun `만료된 access 토큰은 null을 반환한다`() {
        val shortLived = JwtTokenProvider(
            JwtProperties(secret = secret, accessTtlSeconds = -1),
        )
        val expired = shortLived.createAccessToken(userId = 5L, email = "a@b.com", tier = "FREE")
        assertNull(provider.parseAccessSubject(expired))
    }
}
