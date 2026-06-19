package com.readmind.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.readmind.common.ApiResponse
import com.readmind.common.ErrorCode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

/** 인증 실패(토큰 없음/무효) 시 공통 래퍼로 401을 반환한다. */
@Component
class JwtAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper,
) : AuthenticationEntryPoint {

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        response.status = ErrorCode.UNAUTHORIZED.status.value()
        response.contentType = "application/json;charset=UTF-8"
        response.writer.write(
            objectMapper.writeValueAsString(
                ApiResponse.fail(ErrorCode.UNAUTHORIZED.name, "인증이 필요합니다."),
            ),
        )
    }
}
