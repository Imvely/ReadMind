package com.readmind.auth

import com.readmind.auth.jwt.JwtTokenProvider
import com.readmind.common.ApiException
import com.readmind.common.ErrorCode
import com.readmind.config.QuotaProperties
import com.readmind.user.User
import com.readmind.user.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val users: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenProvider: JwtTokenProvider,
    private val quotaProps: QuotaProperties,
) {

    @Transactional
    fun signup(req: SignupRequest): SignupResponse {
        if (users.existsByEmail(req.email)) {
            throw ApiException(ErrorCode.EMAIL_EXISTS, "이미 가입된 이메일입니다.")
        }
        val saved = users.save(
            User(
                email = req.email,
                passwordHash = passwordEncoder.encode(req.password),
                displayName = req.displayName,
            ),
        )
        return SignupResponse(userId = saved.id!!)
    }

    @Transactional(readOnly = true)
    fun login(req: LoginRequest): LoginResponse {
        val user = users.findByEmail(req.email)
            ?: throw ApiException(ErrorCode.INVALID_CREDENTIALS, "이메일 또는 비밀번호가 올바르지 않습니다.")
        val hash = user.passwordHash
        if (hash == null || !passwordEncoder.matches(req.password, hash)) {
            throw ApiException(ErrorCode.INVALID_CREDENTIALS, "이메일 또는 비밀번호가 올바르지 않습니다.")
        }
        val id = user.id!!
        return LoginResponse(
            accessToken = tokenProvider.createAccessToken(id, user.email, user.tier),
            refreshToken = tokenProvider.createRefreshToken(id),
            user = user.toDto(),
        )
    }

    @Transactional(readOnly = true)
    fun refresh(req: RefreshRequest): RefreshResponse {
        val userId = tokenProvider.parseRefreshSubject(req.refreshToken)
            ?: throw ApiException(ErrorCode.TOKEN_INVALID, "유효하지 않은 리프레시 토큰입니다.")
        val user = users.findById(userId).orElseThrow {
            ApiException(ErrorCode.TOKEN_INVALID, "유효하지 않은 리프레시 토큰입니다.")
        }
        return RefreshResponse(
            accessToken = tokenProvider.createAccessToken(user.id!!, user.email, user.tier),
        )
    }

    @Transactional(readOnly = true)
    fun me(userId: Long): MeResponse {
        val user = users.findById(userId).orElseThrow {
            ApiException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다.")
        }
        return MeResponse(
            id = user.id!!,
            email = user.email,
            displayName = user.displayName,
            tier = user.tier,
            quota = buildQuota(user.tier),
        )
    }

    // 사용량 추적/차감은 be-quota-tiers에서 구현. 여기서는 티어별 한도만 노출(used=0).
    private fun buildQuota(tier: String): QuotaInfo {
        val free = tier == "FREE"
        return QuotaInfo(
            summaryUsed = 0,
            summaryLimit = if (free) quotaProps.freeSummaryPerMonth else null,
            qaUsed = 0,
            qaLimit = if (free) quotaProps.freeQaPerMonth else null,
            translateUsed = 0,
            translateLimit = if (free) quotaProps.freeTranslatePerMonth else null,
            storageLimitMb = if (free) quotaProps.freeStorageMb else null,
        )
    }

    private fun User.toDto() = UserDto(id = id!!, email = email, displayName = displayName, tier = tier)
}
