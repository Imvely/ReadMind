package com.readmind.ai

import com.readmind.quota.UsageQuota
import com.readmind.quota.UsageQuotaRepository
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 실제 Postgres 라운드트립 — jsonb 컬럼(@JdbcTypeCode) 매핑 검증 (명세서 §3, §4.4).
 * summaries.content / qa_messages.sources / usage_quotas 가 실제로 저장·조회되는지 확인.
 * @Transactional 롤백으로 DB 오염 0. 실행: ./gradlew integrationTest (Postgres readmind_smoke 필요).
 */
@Tag("integration")
@SpringBootTest
@Transactional
@TestPropertySource(
    properties = [
        "app.jwt.secret=smoke-secret-smoke-secret-smoke-secret-32+",
        "spring.datasource.url=jdbc:postgresql://localhost:5432/readmind_smoke",
        "spring.datasource.username=readmind",
        "spring.datasource.password=readmind",
    ],
)
class AiPersistenceIntegrationTest {

    @Autowired private lateinit var summaries: SummaryRepository
    @Autowired private lateinit var qaSessions: QaSessionRepository
    @Autowired private lateinit var qaMessages: QaMessageRepository
    @Autowired private lateinit var quotas: UsageQuotaRepository
    @Autowired private lateinit var jdbc: JdbcTemplate

    private fun seedUser(): Long = jdbc.queryForObject(
        "INSERT INTO users(email) VALUES ('persist-test@example.com') RETURNING id",
        Long::class.java,
    )!!

    private fun seedDocument(userId: Long): Long = jdbc.queryForObject(
        """
        INSERT INTO documents(user_id, title, format, storage_key, file_size, parse_status)
        VALUES (?, '논문', 'PDF', 'k/1.pdf', 100, 'READY') RETURNING id
        """.trimIndent(),
        Long::class.java,
        userId,
    )!!

    @Test
    fun `summary jsonb content 라운드트립 - scopeRef null 포함`() {
        val docId = seedDocument(seedUser())
        val json = """{"tldr":"요지","keypoints":["a","b"]}"""

        val saved = summaries.save(Summary(docId, "DOCUMENT", null, "PAPER", json))
        summaries.flush()

        val rows = summaries.findByDocumentIdAndScopeAndStyle(docId, "DOCUMENT", "PAPER")
        assertEquals(1, rows.size)
        assertNull(rows[0].scopeRef)
        // jsonb는 키 순서/공백이 재배열될 수 있으니 값 포함으로 확인.
        assertTrue(rows[0].content.contains("\"tldr\""))
        assertTrue(rows[0].content.contains("요지"))
        assertEquals(saved.id, rows[0].id)
    }

    @Test
    fun `qa_message sources jsonb 라운드트립`() {
        val userId = seedUser()
        val docId = seedDocument(userId)
        val session = qaSessions.save(QaSession(userId = userId, documentId = docId))
        qaMessages.save(QaMessage(session.id!!, "USER", "질문?"))
        qaMessages.save(
            QaMessage(session.id!!, "ASSISTANT", "답변", sources = """[{"page":3,"snippet":"근거"}]"""),
        )
        qaMessages.flush()

        val msgs = qaMessages.findBySessionIdOrderByIdAsc(session.id!!)
        assertEquals(2, msgs.size)
        assertEquals("USER", msgs[0].role)
        assertNull(msgs[0].sources)
        assertTrue(msgs[1].sources!!.contains("근거"))
    }

    @Test
    fun `usage_quota 저장 조회`() {
        val userId = seedUser()
        quotas.save(UsageQuota(userId, LocalDate.of(2026, 6, 1), aiSummaryUsed = 1, aiQaUsed = 2))
        quotas.flush()

        val q = quotas.findById(userId).orElseThrow()
        assertEquals(1, q.aiSummaryUsed)
        assertEquals(2, q.aiQaUsed)
        assertEquals(LocalDate.of(2026, 6, 1), q.periodStart)
    }
}
