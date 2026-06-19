package com.readmind.common

import org.springframework.http.HttpStatus

/** 도메인 에러 코드 → HTTP 상태 매핑 (명세서 §4 에러 규약). */
enum class ErrorCode(val status: HttpStatus) {
    VALIDATION(HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED),
    EMAIL_EXISTS(HttpStatus.CONFLICT),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    QUOTA_EXCEEDED(HttpStatus.FORBIDDEN),
    INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR),
}
