package com.readmind.user

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** users 테이블 (명세서 §3). 스키마는 Flyway 소유이므로 JPA는 생성/검증하지 않는다. */
@Entity
@Table(name = "users")
class User(
    @Column(nullable = false, unique = true)
    var email: String,

    @Column(name = "password_hash")
    var passwordHash: String? = null,

    @Column(name = "display_name")
    var displayName: String? = null,

    @Column(nullable = false)
    var tier: String = "FREE",

    @Column(name = "school_verified", nullable = false)
    var schoolVerified: Boolean = false,

    @Column(nullable = false)
    var locale: String = "ko",
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: Instant? = null

    @Column(name = "updated_at", insertable = false, updatable = false)
    var updatedAt: Instant? = null
}
