package com.cheroliv.graphify.apikey

import java.time.LocalDateTime

data class ApiKeyEntry(
    val id: String,
    val email: String,
    val name: String,
    val keyRef: String,
    val provider: Provider,
    val services: List<ServiceType>,
    val expirationDate: LocalDateTime? = null,
    val quota: QuotaConfig = QuotaConfig(),
    val metadata: Map<String, String> = emptyMap()
)

data class QuotaConfig(
    val limitType: QuotaType = QuotaType.REQUESTS,
    val limitValue: Long = 1000,
    val consumedValue: Long = 0,
    val thresholdPercent: Int = 80,
    val periodStart: LocalDateTime? = null,
    val periodEnd: LocalDateTime? = null,
    val resetPolicy: ResetPolicy = ResetPolicy.DAILY,
    val lastManualSync: LocalDateTime? = null
)

data class ApiKeyPoolConfig(
    val version: String = "1.0",
    val poolName: String = "default",
    val rotationStrategy: RotationStrategy = RotationStrategy.ROUND_ROBIN,
    val fallbackEnabled: Boolean = true,
    val auditEnabled: Boolean = true,
    val providers: Map<Provider, List<ApiKeyEntry>> = emptyMap()
)

enum class QuotaType {
    REQUESTS,
    TOKENS,
    DAILY,
    HOURLY,
    MINUTE,
    MONTHLY,
    WEEKLY,
    CUSTOM
}

enum class ResetPolicy {
    DAILY,
    WEEKLY,
    MONTHLY,
    NEVER,
    MANUAL
}

data class AuditLogEntry(
    val timestamp: LocalDateTime,
    val eventType: AuditEventType,
    val entryId: String,
    val provider: Provider,
    val message: String,
    val details: Map<String, String> = emptyMap()
)

enum class AuditEventType {
    USAGE,
    QUOTA_EXCEEDED,
    AUTO_RESET,
    MANUAL_RESET,
    ROTATION,
    ERROR,
    INFO
}
