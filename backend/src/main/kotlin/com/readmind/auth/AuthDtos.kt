package com.readmind.auth

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// ── 요청 ──
data class SignupRequest(
    @field:Email(message = "올바른 이메일이 아닙니다.")
    @field:NotBlank(message = "이메일은 필수입니다.")
    val email: String,
    @field:Size(min = 8, max = 100, message = "비밀번호는 8자 이상이어야 합니다.")
    val password: String,
    val displayName: String? = null,
)

data class LoginRequest(
    @field:NotBlank val email: String,
    @field:NotBlank val password: String,
)

data class RefreshRequest(
    @field:NotBlank val refreshToken: String,
)

// ── 응답 ──
data class SignupResponse(val userId: Long)

data class UserDto(
    val id: Long,
    val email: String,
    val displayName: String?,
    val tier: String,
)

data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserDto,
)

data class RefreshResponse(val accessToken: String)

data class QuotaInfo(
    val summaryUsed: Int,
    val summaryLimit: Int?,
    val qaUsed: Int,
    val qaLimit: Int?,
    val translateUsed: Int,
    val translateLimit: Int?,
    val storageLimitMb: Int?,
)

data class MeResponse(
    val id: Long,
    val email: String,
    val displayName: String?,
    val tier: String,
    val quota: QuotaInfo,
)
