package com.readmind.document

import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.time.Instant

// ── 요청 ──
/** 업로드 초기화. 클라이언트는 받은 uploadUrl로 S3에 직접 PUT 후 complete를 호출한다. */
data class CreateDocumentRequest(
    @field:NotBlank(message = "제목은 필수입니다.")
    @field:Size(max = 500, message = "제목은 500자 이하여야 합니다.")
    val title: String,
    @field:NotBlank(message = "포맷은 필수입니다.")
    val format: String,
    @field:Positive(message = "파일 크기는 0보다 커야 합니다.")
    val fileSize: Long,
)

// ── 응답 ──
data class CreateDocumentResponse(
    val documentId: Long,
    val uploadUrl: String,
)

data class CompleteResponse(
    val documentId: Long,
    val parseStatus: ParseStatus,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class DocumentDto(
    val id: Long,
    val title: String,
    val format: String,
    val fileSize: Long,
    val pageCount: Int?,
    val language: String?,
    val parseStatus: ParseStatus,
    val createdAt: Instant?,
)

/** 목록 페이지네이션 (명세서 §4: data.items[], totalElements, hasNext). */
data class DocumentListResponse(
    val items: List<DocumentDto>,
    val totalElements: Long,
    val hasNext: Boolean,
)

/** 렌더용 원문 접근 — presigned GET URL(권한 확인 후 발급). */
data class DocumentContentResponse(
    val url: String,
)
