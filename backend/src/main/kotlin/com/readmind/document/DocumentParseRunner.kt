package com.readmind.document

import com.readmind.document.ai.AiParseClient
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 업로드 완료 후 AI 파싱을 비동기로 실행하고 documents.parse_status를 갱신한다 (명세서 §4.2).
 * 별도 빈으로 둬 @Async 프록시가 적용되게 한다(자기호출 시 @Async 미적용).
 * AI 산출(language/page_count)은 백엔드가 소유해 여기서 반영한다(ai-parse 결정사항).
 */
@Component
class DocumentParseRunner(
    private val documents: DocumentRepository,
    private val aiParseClient: AiParseClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async("parseExecutor")
    @Transactional
    fun run(documentId: Long) {
        val doc = documents.findById(documentId).orElse(null) ?: run {
            log.warn("parse 트리거된 문서를 찾을 수 없음: id={}", documentId)
            return
        }
        try {
            val result = aiParseClient.parse(doc.id!!, doc.storageKey, doc.format)
            doc.pageCount = result.pageCount
            doc.language = result.language
            doc.parseStatus = ParseStatus.READY
            documents.save(doc)
            log.info("파싱 완료: id={}, chunks={}, pages={}", documentId, result.chunkCount, result.pageCount)
        } catch (ex: Exception) {
            doc.parseStatus = ParseStatus.FAILED
            documents.save(doc)
            log.error("파싱 실패: id={}", documentId, ex)
        }
    }
}
