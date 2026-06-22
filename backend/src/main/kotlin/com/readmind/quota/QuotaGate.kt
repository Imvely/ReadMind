package com.readmind.quota

import com.readmind.common.ApiException
import com.readmind.common.ErrorCode
import com.readmind.config.QuotaProperties
import com.readmind.user.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

/** 쿼터 차감 대상 AI 기능. */
enum class QuotaKind { SUMMARY, QA, TRANSLATE }

/**
 * AI 변동비 사전 차단 게이트 (명세서 §4.4, CLAUDE.md §3 "AI는 변동비").
 *
 * 사용 순서(강제): ensureWithin(검사) → [캐시 조회] → AI 호출 → [캐시 저장] → record(차감).
 * - 캐시 히트 시 record를 호출하지 않는다 → 변동비 0이면 쿼터도 소모하지 않는다.
 * - FREE만 한도를 적용한다. PRO/STUDENT는 Phase 0에서 무제한(전면 매트릭스는 be-quota-tiers).
 * - 월 단위 집계: period_start 달이 지나면 카운터를 0으로 리셋한다.
 */
@Service
class QuotaGate(
    private val quotas: UsageQuotaRepository,
    private val users: UserRepository,
    private val props: QuotaProperties,
    private val clock: Clock,
) {

    /** 한도 초과면 호출 전에 차단(403 QUOTA_EXCEEDED). FREE에만 적용. */
    @Transactional(readOnly = true)
    fun ensureWithin(userId: Long, kind: QuotaKind) {
        if (tierOf(userId) != FREE_TIER) return
        val used = effectiveUsed(userId, kind)
        if (used >= limitOf(kind)) {
            throw ApiException(
                ErrorCode.QUOTA_EXCEEDED,
                "무료 사용량을 모두 소진했습니다(${kind.name}: $used/${limitOf(kind)}). 업그레이드하면 계속 사용할 수 있어요.",
            )
        }
    }

    /** 실제 AI 호출(변동비 발생) 성공 후에만 1 차감. 월이 바뀌었으면 먼저 리셋. */
    @Transactional
    fun record(userId: Long, kind: QuotaKind) {
        val q = loadForCurrentPeriod(userId)
        when (kind) {
            QuotaKind.SUMMARY -> q.aiSummaryUsed += 1
            QuotaKind.QA -> q.aiQaUsed += 1
            QuotaKind.TRANSLATE -> q.aiTranslateUsed += 1
        }
        quotas.save(q)
    }

    // ── 내부 ──

    private fun tierOf(userId: Long): String =
        users.findById(userId).orElseThrow {
            ApiException(ErrorCode.UNAUTHORIZED, "사용자를 찾을 수 없습니다.")
        }.tier

    /** 현재 기간의 사용량. 기간이 과거면 0으로 간주(리셋은 record 시점에 영속화). */
    private fun effectiveUsed(userId: Long, kind: QuotaKind): Int {
        val q = quotas.findById(userId).orElse(null) ?: return 0
        if (q.periodStart != currentPeriodStart()) return 0
        return when (kind) {
            QuotaKind.SUMMARY -> q.aiSummaryUsed
            QuotaKind.QA -> q.aiQaUsed
            QuotaKind.TRANSLATE -> q.aiTranslateUsed
        }
    }

    private fun loadForCurrentPeriod(userId: Long): UsageQuota {
        val period = currentPeriodStart()
        val q = quotas.findById(userId).orElseGet {
            UsageQuota(userId = userId, periodStart = period)
        }
        if (q.periodStart != period) {
            // 달이 바뀌었다 → 새 기간으로 리셋.
            q.periodStart = period
            q.aiSummaryUsed = 0
            q.aiQaUsed = 0
            q.aiTranslateUsed = 0
        }
        return q
    }

    private fun currentPeriodStart(): LocalDate = LocalDate.now(clock).withDayOfMonth(1)

    private fun limitOf(kind: QuotaKind): Int = when (kind) {
        QuotaKind.SUMMARY -> props.freeSummaryPerMonth
        QuotaKind.QA -> props.freeQaPerMonth
        QuotaKind.TRANSLATE -> props.freeTranslatePerMonth
    }

    private companion object {
        const val FREE_TIER = "FREE"
    }
}
