package com.readmind.document.ai

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import kotlin.test.assertEquals

/** retryTransient — 일시 오류만 백오프 재시도, 영구 오류는 즉시 전파 (§4.2). */
class RetryTest {

    private val sleeps = mutableListOf<Long>()
    private val noSleep: (Long) -> Unit = { sleeps.add(it) }

    @Test
    fun `5xx는 재시도 후 성공하면 결과 반환`() {
        var calls = 0
        val result = retryTransient(maxAttempts = 4, initialBackoffMs = 100, sleep = noSleep) {
            calls++
            if (calls < 3) throw HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE)
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(3, calls)
        assertEquals(listOf(100L, 200L), sleeps) // 백오프 2배 증가.
    }

    @Test
    fun `연결 실패,타임아웃(ResourceAccessException)도 재시도`() {
        var calls = 0
        retryTransient(maxAttempts = 2, initialBackoffMs = 1, sleep = noSleep) {
            calls++
            if (calls == 1) throw ResourceAccessException("connect timed out")
            "ok"
        }
        assertEquals(2, calls)
    }

    @Test
    fun `429는 재시도`() {
        var calls = 0
        retryTransient(maxAttempts = 2, initialBackoffMs = 1, sleep = noSleep) {
            calls++
            if (calls == 1) throw HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS)
            "ok"
        }
        assertEquals(2, calls)
    }

    @Test
    fun `4xx 영구 오류는 즉시 던짐(재시도 없음)`() {
        var calls = 0
        assertThrows<HttpClientErrorException> {
            retryTransient(maxAttempts = 4, initialBackoffMs = 1, sleep = noSleep) {
                calls++
                throw HttpClientErrorException(HttpStatus.UNPROCESSABLE_ENTITY)
            }
        }
        assertEquals(1, calls)
        assertEquals(emptyList(), sleeps)
    }

    @Test
    fun `일시 오류라도 maxAttempts 소진하면 마지막 예외 전파`() {
        var calls = 0
        assertThrows<HttpServerErrorException> {
            retryTransient(maxAttempts = 3, initialBackoffMs = 1, sleep = noSleep) {
                calls++
                throw HttpServerErrorException(HttpStatus.BAD_GATEWAY)
            }
        }
        assertEquals(3, calls)
        assertEquals(2, sleeps.size) // 마지막 시도 후엔 대기 없음.
    }
}
