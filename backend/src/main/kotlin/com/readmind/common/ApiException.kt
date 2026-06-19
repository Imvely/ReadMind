package com.readmind.common

/** 도메인 예외. GlobalExceptionHandler가 공통 래퍼로 변환한다. */
class ApiException(
    val code: ErrorCode,
    override val message: String,
) : RuntimeException(message)
