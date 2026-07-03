package com.readmind.document.ai

import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.ResourceAccessException

/**
 * 일시 오류만 백오프 재시도한다 (명세서 §4.2 — AI 서비스 콜드스타트/재배포 창 대응).
 *
 * 재시도 대상: 연결 실패·타임아웃(ResourceAccessException), 5xx(HttpServerErrorException), 429.
 * 4xx 등 영구 오류는 즉시 던진다(재시도해도 같은 결과).
 *
 * @param sleep 테스트에서 실제 대기를 치환하기 위한 주입점.
 */
internal fun <T> retryTransient(
    maxAttempts: Int,
    initialBackoffMs: Long,
    sleep: (Long) -> Unit = Thread::sleep,
    block: () -> T,
): T {
    var backoffMs = initialBackoffMs
    var attempt = 1
    while (true) {
        try {
            return block()
        } catch (ex: Exception) {
            if (!isTransient(ex) || attempt >= maxAttempts) throw ex
            sleep(backoffMs)
            backoffMs *= 2
            attempt++
        }
    }
}

private fun isTransient(ex: Exception): Boolean = when (ex) {
    is ResourceAccessException -> true // 연결 실패·타임아웃.
    is HttpServerErrorException -> true // 5xx.
    is HttpStatusCodeException -> ex.statusCode.value() == 429 // 레이트리밋.
    else -> false
}
