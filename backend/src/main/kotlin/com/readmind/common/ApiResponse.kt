package com.readmind.common

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * 공통 응답 래퍼 (명세서 §4).
 * 성공: { "success": true, "data": {...} }
 * 실패: { "success": false, "error": { "code": "...", "message": "..." } }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ApiError? = null,
) {
    companion object {
        fun <T> ok(data: T): ApiResponse<T> = ApiResponse(success = true, data = data)

        fun fail(code: String, message: String): ApiResponse<Nothing> =
            ApiResponse(success = false, error = ApiError(code, message))
    }
}

data class ApiError(val code: String, val message: String)
