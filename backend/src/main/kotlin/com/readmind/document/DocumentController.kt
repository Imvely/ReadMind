package com.readmind.document

import com.readmind.common.ApiException
import com.readmind.common.ApiResponse
import com.readmind.common.ErrorCode
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 문서 API (명세서 §4.2). base path는 /api/v1(context-path) + /documents. */
@RestController
@RequestMapping("/documents")
class DocumentController(
    private val service: DocumentService,
) {

    @PostMapping
    fun create(
        @AuthenticationPrincipal userId: Long?,
        @Valid @RequestBody req: CreateDocumentRequest,
    ): ApiResponse<CreateDocumentResponse> =
        ApiResponse.ok(service.create(requireUser(userId), req))

    @PostMapping("/{id}/complete")
    fun complete(
        @AuthenticationPrincipal userId: Long?,
        @PathVariable id: Long,
    ): ApiResponse<CompleteResponse> =
        ApiResponse.ok(service.complete(requireUser(userId), id))

    @GetMapping
    fun list(
        @AuthenticationPrincipal userId: Long?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<DocumentListResponse> {
        val pageable = PageRequest.of(
            page.coerceAtLeast(0),
            size.coerceIn(1, MAX_PAGE_SIZE),
            Sort.by(Sort.Direction.DESC, "createdAt"),
        )
        return ApiResponse.ok(service.list(requireUser(userId), pageable))
    }

    @GetMapping("/{id}")
    fun get(
        @AuthenticationPrincipal userId: Long?,
        @PathVariable id: Long,
    ): ApiResponse<DocumentDto> =
        ApiResponse.ok(service.get(requireUser(userId), id))

    @GetMapping("/{id}/content")
    fun content(
        @AuthenticationPrincipal userId: Long?,
        @PathVariable id: Long,
    ): ApiResponse<DocumentContentResponse> =
        ApiResponse.ok(service.content(requireUser(userId), id))

    @DeleteMapping("/{id}")
    fun delete(
        @AuthenticationPrincipal userId: Long?,
        @PathVariable id: Long,
    ): ApiResponse<Unit> {
        service.delete(requireUser(userId), id)
        return ApiResponse.ok(Unit)
    }

    private fun requireUser(userId: Long?): Long =
        userId ?: throw ApiException(ErrorCode.UNAUTHORIZED, "인증이 필요합니다.")

    private companion object {
        const val MAX_PAGE_SIZE = 100
    }
}
