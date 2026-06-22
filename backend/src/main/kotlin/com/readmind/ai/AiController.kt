package com.readmind.ai

import com.readmind.common.ApiException
import com.readmind.common.ApiResponse
import com.readmind.common.ErrorCode
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * AI 위임 API (명세서 §4.4). base path = /api/v1(context-path) + /documents/{id}/...
 * 쿼터 게이트·캐시·소유권은 AiService가 강제(순서 보장).
 */
@RestController
@RequestMapping("/documents/{id}")
class AiController(
    private val service: AiService,
) {

    @PostMapping("/summarize")
    fun summarize(
        @AuthenticationPrincipal userId: Long?,
        @PathVariable id: Long,
        @Valid @RequestBody req: SummarizeRequest,
    ): ApiResponse<SummarizeResponse> =
        ApiResponse.ok(service.summarize(requireUser(userId), id, req))

    @PostMapping("/qa")
    fun qa(
        @AuthenticationPrincipal userId: Long?,
        @PathVariable id: Long,
        @Valid @RequestBody req: QaRequest,
    ): ApiResponse<QaResponse> =
        ApiResponse.ok(service.qa(requireUser(userId), id, req))

    private fun requireUser(userId: Long?): Long =
        userId ?: throw ApiException(ErrorCode.UNAUTHORIZED, "인증이 필요합니다.")
}
