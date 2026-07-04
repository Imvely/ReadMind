package com.readmind.sync

import com.readmind.common.ApiException
import com.readmind.common.ApiResponse
import com.readmind.common.ErrorCode
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/** 증분 동기화 API (명세서 §4.5). base = /api/v1(context-path) + /sync. */
@RestController
@RequestMapping("/sync")
class SyncController(
    private val service: SyncService,
) {

    @GetMapping("/changes")
    fun changes(
        @AuthenticationPrincipal userId: Long?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        since: Instant?,
    ): ApiResponse<SyncChangesResponse> =
        ApiResponse.ok(service.changes(requireUser(userId), since))

    @PostMapping("/push")
    fun push(
        @AuthenticationPrincipal userId: Long?,
        @Valid @RequestBody req: SyncPushRequest,
    ): ApiResponse<SyncPushResponse> =
        ApiResponse.ok(service.push(requireUser(userId), req))

    private fun requireUser(userId: Long?): Long =
        userId ?: throw ApiException(ErrorCode.UNAUTHORIZED, "인증이 필요합니다.")
}
