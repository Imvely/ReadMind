package com.readmind.auth.jwt

import com.readmind.config.JwtProperties
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.Date
import javax.crypto.SecretKey

/** access/refresh JWT 발급·검증 (HS256). 토큰 type 클레임으로 용도를 구분한다. */
@Component
class JwtTokenProvider(props: JwtProperties) {

    private val key: SecretKey = Keys.hmacShaKeyFor(props.secret.toByteArray()).also {
        require(props.secret.toByteArray().size >= 32) {
            "JWT secret은 32바이트 이상이어야 한다(HS256)."
        }
    }
    private val accessTtl = props.accessTtlSeconds
    private val refreshTtl = props.refreshTtlSeconds

    fun createAccessToken(userId: Long, email: String, tier: String): String =
        build(userId, TYPE_ACCESS, accessTtl, mapOf("email" to email, "tier" to tier))

    fun createRefreshToken(userId: Long): String =
        build(userId, TYPE_REFRESH, refreshTtl, emptyMap())

    /** access 토큰을 검증하고 userId를 반환. 실패 시 null. */
    fun parseAccessSubject(token: String): Long? = subjectIfType(token, TYPE_ACCESS)

    /** refresh 토큰을 검증하고 userId를 반환. 실패 시 null. */
    fun parseRefreshSubject(token: String): Long? = subjectIfType(token, TYPE_REFRESH)

    private fun build(
        userId: Long,
        type: String,
        ttlSeconds: Long,
        claims: Map<String, Any>,
    ): String {
        val now = Instant.now()
        return Jwts.builder()
            .subject(userId.toString())
            .claim(CLAIM_TYPE, type)
            .claims(claims)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(ttlSeconds)))
            .signWith(key)
            .compact()
    }

    private fun subjectIfType(token: String, expectedType: String): Long? = try {
        val claims = Jwts.parser().verifyWith(key).build()
            .parseSignedClaims(token).payload
        if (claims[CLAIM_TYPE] != expectedType) null
        else claims.subject?.toLongOrNull()
    } catch (_: JwtException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private companion object {
        const val CLAIM_TYPE = "type"
        const val TYPE_ACCESS = "access"
        const val TYPE_REFRESH = "refresh"
    }
}
