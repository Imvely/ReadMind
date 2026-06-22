package com.readmind.document

import com.readmind.common.ApiException
import com.readmind.common.ErrorCode
import com.readmind.document.storage.DocumentStorage
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class DocumentService(
    private val documents: DocumentRepository,
    private val storage: DocumentStorage,
    private val parseRunner: DocumentParseRunner,
) {

    /** 업로드 초기화: documents row(PENDING) 생성 + presigned PUT URL 발급. */
    @Transactional
    fun create(userId: Long, req: CreateDocumentRequest): CreateDocumentResponse {
        val format = normalizeFormat(req.format)
        val storageKey = storage.newStorageKey(userId, format)
        val doc = documents.save(
            Document(
                userId = userId,
                title = req.title,
                format = format,
                storageKey = storageKey,
                fileSize = req.fileSize,
            ),
        )
        return CreateDocumentResponse(
            documentId = doc.id!!,
            uploadUrl = storage.presignPut(storageKey),
        )
    }

    /** 업로드 완료 통지: 상태를 PARSING으로 올리고 AI 파싱을 비동기 트리거. */
    @Transactional
    fun complete(userId: Long, documentId: Long): CompleteResponse {
        val doc = ownedOrThrow(documentId, userId)
        if (doc.parseStatus == ParseStatus.READY) {
            // 이미 완료된 문서는 재파싱하지 않는다(캐싱 철학, §3).
            return CompleteResponse(doc.id!!, doc.parseStatus)
        }
        doc.parseStatus = ParseStatus.PARSING
        documents.save(doc)
        parseRunner.run(doc.id!!)
        return CompleteResponse(doc.id!!, ParseStatus.PARSING)
    }

    @Transactional(readOnly = true)
    fun list(userId: Long, pageable: Pageable): DocumentListResponse {
        val page = documents.findByUserIdAndDeletedAtIsNull(userId, pageable)
        return DocumentListResponse(
            items = page.content.map { it.toDto() },
            totalElements = page.totalElements,
            hasNext = page.hasNext(),
        )
    }

    @Transactional(readOnly = true)
    fun get(userId: Long, documentId: Long): DocumentDto = ownedOrThrow(documentId, userId).toDto()

    /** 렌더용 presigned GET URL — 소유권 확인 후에만 발급. */
    @Transactional(readOnly = true)
    fun content(userId: Long, documentId: Long): DocumentContentResponse {
        val doc = ownedOrThrow(documentId, userId)
        return DocumentContentResponse(url = storage.presignGet(doc.storageKey))
    }

    /** 소프트 삭제(tombstone). 실제 객체/청크 정리는 별도 정책. */
    @Transactional
    fun delete(userId: Long, documentId: Long) {
        val doc = ownedOrThrow(documentId, userId)
        doc.deletedAt = Instant.now()
        documents.save(doc)
    }

    // ── 내부 ──
    private fun ownedOrThrow(documentId: Long, userId: Long): Document =
        documents.findByIdAndUserIdAndDeletedAtIsNull(documentId, userId)
            ?: throw ApiException(ErrorCode.NOT_FOUND, "문서를 찾을 수 없습니다.")

    private fun normalizeFormat(raw: String): String {
        val format = raw.trim().uppercase()
        if (format !in SUPPORTED_FORMATS) {
            throw ApiException(ErrorCode.VALIDATION, "지원하지 않는 포맷입니다: $raw")
        }
        return format
    }

    private fun Document.toDto() = DocumentDto(
        id = id!!,
        title = title,
        format = format,
        fileSize = fileSize,
        pageCount = pageCount,
        language = language,
        parseStatus = parseStatus,
        createdAt = createdAt,
    )

    private companion object {
        // Phase 0은 PDF만(AI 파서도 pdf만). 새 포맷은 ai-parse-multi에서 확장.
        val SUPPORTED_FORMATS = setOf("PDF")
    }
}
