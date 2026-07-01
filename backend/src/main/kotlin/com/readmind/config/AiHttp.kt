package com.readmind.config

import org.springframework.http.client.JdkClientHttpRequestFactory
import java.net.http.HttpClient

/**
 * AI 서비스(FastAPI/uvicorn·httptools) 호출용 요청 팩토리.
 *
 * JDK HttpClient 기본값은 HTTP/2 라, cleartext(http) 에서 h2c 업그레이드(Upgrade: h2c)를 보낸다.
 * uvicorn(httptools)은 이를 "Unsupported upgrade request → Invalid HTTP request(400)"로 거부한다.
 * 그래서 AI 호출은 HTTP/1.1 로 고정한다.
 */
fun http11RequestFactory(): JdkClientHttpRequestFactory =
    JdkClientHttpRequestFactory(
        HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build(),
    )
