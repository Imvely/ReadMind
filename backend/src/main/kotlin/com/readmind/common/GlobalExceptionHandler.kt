package com.readmind.common

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ApiException::class)
    fun handleApi(ex: ApiException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity
            .status(ex.code.status)
            .body(ApiResponse.fail(ex.code.name, ex.message))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(
        ex: MethodArgumentNotValidException,
    ): ResponseEntity<ApiResponse<Nothing>> {
        val msg = ex.bindingResult.fieldErrors
            .joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
            .ifBlank { "잘못된 요청입니다." }
        return ResponseEntity
            .status(ErrorCode.VALIDATION.status)
            .body(ApiResponse.fail(ErrorCode.VALIDATION.name, msg))
    }
}
