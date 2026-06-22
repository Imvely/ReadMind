package com.readmind.quota

import com.readmind.common.ApiException
import com.readmind.common.ErrorCode
import com.readmind.config.QuotaProperties
import com.readmind.user.User
import com.readmind.user.UserRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Optional
import kotlin.test.assertEquals

/** 쿼터 게이트 단위 — FREE 한도 차단/PRO 무제한/월 리셋/차감 (명세서 §4.4, §3). */
class QuotaGateTest {

    private val quotas = mock<UsageQuotaRepository>()
    private val users = mock<UserRepository>()
    private val props = QuotaProperties(
        freeSummaryPerMonth = 2,
        freeQaPerMonth = 3,
        freeTranslatePerMonth = 1,
    )
    // 2026-06-22 고정 → 현재 기간 시작 2026-06-01.
    private val clock = Clock.fixed(Instant.parse("2026-06-22T09:00:00Z"), ZoneOffset.UTC)
    private val gate = QuotaGate(quotas, users, props, clock)

    private val userId = 10L
    private val period = LocalDate.of(2026, 6, 1)

    private fun user(tier: String) = User(email = "a@b.com", tier = tier).apply { id = userId }

    @Test
    fun `FREE 한도 내면 통과`() {
        whenever(users.findById(userId)).doReturn(Optional.of(user("FREE")))
        whenever(quotas.findById(userId)).doReturn(
            Optional.of(UsageQuota(userId, period, aiSummaryUsed = 1)),
        )
        gate.ensureWithin(userId, QuotaKind.SUMMARY) // 1 < 2 → 통과(예외 없음)
    }

    @Test
    fun `FREE 한도 도달이면 QUOTA_EXCEEDED`() {
        whenever(users.findById(userId)).doReturn(Optional.of(user("FREE")))
        whenever(quotas.findById(userId)).doReturn(
            Optional.of(UsageQuota(userId, period, aiSummaryUsed = 2)),
        )
        val ex = assertThrows<ApiException> { gate.ensureWithin(userId, QuotaKind.SUMMARY) }
        assertEquals(ErrorCode.QUOTA_EXCEEDED, ex.code)
    }

    @Test
    fun `PRO는 한도 무시하고 통과`() {
        whenever(users.findById(userId)).doReturn(Optional.of(user("PRO")))
        // 쿼터 행을 보지 않아도 통과해야 한다.
        gate.ensureWithin(userId, QuotaKind.QA)
        verify(quotas, never()).findById(any())
    }

    @Test
    fun `지난 달 사용량은 0으로 간주`() {
        whenever(users.findById(userId)).doReturn(Optional.of(user("FREE")))
        whenever(quotas.findById(userId)).doReturn(
            Optional.of(UsageQuota(userId, LocalDate.of(2026, 5, 1), aiQaUsed = 99)),
        )
        gate.ensureWithin(userId, QuotaKind.QA) // 과거 기간 → 0 < 3 → 통과
    }

    @Test
    fun `record - 행 없으면 생성하고 1 차감`() {
        whenever(quotas.findById(userId)).doReturn(Optional.empty())
        whenever(quotas.save(any())).doReturn(mock())

        gate.record(userId, QuotaKind.SUMMARY)

        val captor = argumentCaptor<UsageQuota>()
        verify(quotas).save(captor.capture())
        assertEquals(1, captor.firstValue.aiSummaryUsed)
        assertEquals(period, captor.firstValue.periodStart)
    }

    @Test
    fun `record - 지난 기간이면 리셋 후 1 차감`() {
        whenever(quotas.findById(userId)).doReturn(
            Optional.of(UsageQuota(userId, LocalDate.of(2026, 5, 1), aiQaUsed = 50, aiSummaryUsed = 7)),
        )
        whenever(quotas.save(any())).doReturn(mock())

        gate.record(userId, QuotaKind.QA)

        val captor = argumentCaptor<UsageQuota>()
        verify(quotas).save(captor.capture())
        assertEquals(period, captor.firstValue.periodStart)
        assertEquals(1, captor.firstValue.aiQaUsed)  // 50 → 리셋 → 1
        assertEquals(0, captor.firstValue.aiSummaryUsed) // 7 → 리셋 → 0
    }
}
