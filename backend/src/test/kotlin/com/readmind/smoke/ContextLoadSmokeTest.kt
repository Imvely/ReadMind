package com.readmind.smoke

import com.readmind.document.DocumentController
import com.readmind.document.DocumentService
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * 전체 Spring 컨텍스트 부팅 스모크 (인프라 필요).
 * - 모든 빈(S3Config/AsyncConfig/Document*·Auth*) 와이어링 + Flyway V1 마이그레이션 적용 확인.
 * - @Tag("integration") → 기본 ./gradlew test 에서는 제외. 실행: ./gradlew integrationTest
 *   (Postgres readmind_smoke DB가 localhost:5432에 있어야 함.)
 */
@Tag("integration")
@SpringBootTest
@TestPropertySource(
    properties = [
        "app.jwt.secret=smoke-secret-smoke-secret-smoke-secret-32+",
        "spring.datasource.url=jdbc:postgresql://localhost:5432/readmind_smoke",
        "spring.datasource.username=readmind",
        "spring.datasource.password=readmind",
    ],
)
class ContextLoadSmokeTest {

    @Autowired private lateinit var documentController: DocumentController
    @Autowired private lateinit var documentService: DocumentService
    @Autowired private lateinit var s3Presigner: S3Presigner
    @Autowired private lateinit var dataSource: DataSource

    @Test
    fun `컨텍스트가 부팅되고 핵심 빈이 와이어링된다`() {
        assertNotNull(documentController)
        assertNotNull(documentService)
        assertNotNull(s3Presigner)
    }

    @Test
    fun `Flyway V1 마이그레이션으로 documents 테이블과 pgvector가 적용된다`() {
        dataSource.connection.use { conn ->
            conn.createStatement().use { st ->
                // documents 테이블 존재
                st.executeQuery("SELECT to_regclass('public.documents')").use { rs ->
                    rs.next()
                    assertNotNull(rs.getString(1), "documents 테이블이 있어야 함")
                }
                // pgvector 확장 등록(document_chunks.embedding vector 의존)
                st.executeQuery("SELECT count(*) FROM pg_extension WHERE extname='vector'").use { rs ->
                    rs.next()
                    assertEquals(1, rs.getInt(1), "vector 확장이 설치돼야 함")
                }
            }
        }
    }
}
