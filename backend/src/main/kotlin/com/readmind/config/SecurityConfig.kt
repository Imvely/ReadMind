package com.readmind.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class SecurityConfig(
    private val jwtAuthFilter: JwtAuthFilter,
    private val entryPoint: JwtAuthenticationEntryPoint,
    /** 정적 웹 오리진의 직접 호출 허용 목록(콤마 구분). 빈 값(기본)이면 CORS 미허용 = 기존 동작. */
    @Value("\${app.cors.allowed-origins:}") private val corsAllowedOrigins: String,
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    /**
     * env CORS_ALLOWED_ORIGINS 기반 CORS. 인증은 Authorization 헤더(JWT)라 쿠키가 없으므로
     * allowCredentials 는 켜지 않는다. 오리진 미설정 시 어떤 교차 출처도 허용하지 않는다.
     */
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val source = UrlBasedCorsConfigurationSource()
        val origins = corsAllowedOrigins.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (origins.isNotEmpty()) {
            val cfg = CorsConfiguration().apply {
                allowedOrigins = origins
                allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                allowedHeaders = listOf("Authorization", "Content-Type")
                maxAge = 3600
            }
            source.registerCorsConfiguration("/**", cfg)
        }
        return source
    }

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { }
            .csrf { it.disable() }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/auth/signup", "/auth/login", "/auth/refresh").permitAll()
                it.anyRequest().authenticated()
            }
            .exceptionHandling { it.authenticationEntryPoint(entryPoint) }
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }
}
