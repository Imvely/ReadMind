package com.readmind.auth

import com.readmind.common.ApiException
import com.readmind.common.ApiResponse
import com.readmind.common.ErrorCode
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 인증 API (명세서 §4.1). base path는 /api/v1(context-path) + /auth. */
@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService,
) {

    @PostMapping("/signup")
    fun signup(@Valid @RequestBody req: SignupRequest): ApiResponse<SignupResponse> =
        ApiResponse.ok(authService.signup(req))

    @PostMapping("/login")
    fun login(@Valid @RequestBody req: LoginRequest): ApiResponse<LoginResponse> =
        ApiResponse.ok(authService.login(req))

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody req: RefreshRequest): ApiResponse<RefreshResponse> =
        ApiResponse.ok(authService.refresh(req))

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal userId: Long?): ApiResponse<MeResponse> {
        val id = userId ?: throw ApiException(ErrorCode.UNAUTHORIZED, "인증이 필요합니다.")
        return ApiResponse.ok(authService.me(id))
    }
}
