package com.readmind.config

import org.springframework.http.client.JdkClientHttpRequestFactory
import java.net.http.HttpClient
import java.time.Duration

/**
 * AI 서비스(FastAPI/uvicorn·httptools) 호출용 요청 팩토리.
 *
 * JDK HttpClient 기본값은 HTTP/2 라, cleartext(http) 에서 h2c 업그레이드(Upgrade: h2c)를 보낸다.
 * uvicorn(httptools)은 이를 "Unsupported upgrade request → Invalid HTTP request(400)"로 거부한다.
 * 그래서 AI 호출은 HTTP/1.1 로 고정한다.
 *
 * JDK HttpClient는 기본 요청 타임아웃이 없어(무한 대기) AI 서비스가 응답을 못 주면
 * @Async 스레드가 영원히 잠긴다 — readTimeout 을 반드시 건다.
 */
fun http11RequestFactory(readTimeout: Duration? = null): JdkClientHttpRequestFactory =
    JdkClientHttpRequestFactory(
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(CONNECT_TIMEOUT)
            .build(),
    ).apply { readTimeout?.let { setReadTimeout(it) } }

private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
