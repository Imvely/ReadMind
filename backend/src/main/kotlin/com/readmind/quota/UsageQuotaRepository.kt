package com.readmind.quota

import org.springframework.data.jpa.repository.JpaRepository

interface UsageQuotaRepository : JpaRepository<UsageQuota, Long>
