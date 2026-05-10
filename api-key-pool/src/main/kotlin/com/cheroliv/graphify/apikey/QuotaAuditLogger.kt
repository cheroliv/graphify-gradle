package com.cheroliv.graphify.apikey

import java.time.LocalDateTime

class QuotaAuditLogger(
    private val enabled: Boolean = true
) {
    private val logs = mutableListOf<AuditLogEntry>()

    fun logUsage(entry: ApiKeyEntry, usageCount: Long) {
        if (!enabled) return
        logs.add(
            AuditLogEntry(
                timestamp = LocalDateTime.now(),
                eventType = AuditEventType.USAGE,
                entryId = entry.id,
                provider = entry.provider,
                message = "Usage tracked: $usageCount requests",
                details = mapOf(
                    "usageCount" to usageCount.toString(),
                    "limitValue" to entry.quota.limitValue.toString(),
                    "thresholdPercent" to entry.quota.thresholdPercent.toString()
                )
            )
        )
    }

    fun logQuotaExceeded(entry: ApiKeyEntry, usageCount: Long) {
        if (!enabled) return
        logs.add(
            AuditLogEntry(
                timestamp = LocalDateTime.now(),
                eventType = AuditEventType.QUOTA_EXCEEDED,
                entryId = entry.id,
                provider = entry.provider,
                message = "Quota threshold exceeded: $usageCount / ${entry.quota.limitValue}",
                details = mapOf(
                    "usageCount" to usageCount.toString(),
                    "limitValue" to entry.quota.limitValue.toString(),
                    "thresholdPercent" to entry.quota.thresholdPercent.toString()
                )
            )
        )
    }

    fun logReset(entryId: String, resetCount: Int, isManual: Boolean) {
        if (!enabled) return
        logs.add(
            AuditLogEntry(
                timestamp = LocalDateTime.now(),
                eventType = if (isManual) AuditEventType.MANUAL_RESET else AuditEventType.AUTO_RESET,
                entryId = entryId,
                provider = Provider.UNKNOWN,
                message = "Quota reset performed (${if (isManual) "manual" else "automatic"})",
                details = mapOf(
                    "resetCount" to resetCount.toString()
                )
            )
        )
    }

    fun logRotation(fromEntry: ApiKeyEntry, toEntry: ApiKeyEntry, reason: String) {
        if (!enabled) return
        logs.add(
            AuditLogEntry(
                timestamp = LocalDateTime.now(),
                eventType = AuditEventType.ROTATION,
                entryId = toEntry.id,
                provider = toEntry.provider,
                message = "Key rotation: ${fromEntry.id} -> ${toEntry.id}",
                details = mapOf(
                    "fromEntryId" to fromEntry.id,
                    "toEntryId" to toEntry.id,
                    "reason" to reason
                )
            )
        )
    }

    fun getLogs(): List<AuditLogEntry> = logs.toList()

    fun getLogsForEntry(entryId: String): List<AuditLogEntry> {
        return logs.filter { it.entryId == entryId }
    }

    fun getLogsByType(eventType: AuditEventType): List<AuditLogEntry> {
        return logs.filter { it.eventType == eventType }
    }

    fun logInfo(provider: Provider, message: String) {
        if (!enabled) return
        logs.add(
            AuditLogEntry(
                timestamp = LocalDateTime.now(),
                eventType = AuditEventType.INFO,
                entryId = "system",
                provider = provider,
                message = message,
                details = emptyMap()
            )
        )
    }

    fun logError(provider: Provider, message: String, error: Throwable? = null) {
        if (!enabled) return
        logs.add(
            AuditLogEntry(
                timestamp = LocalDateTime.now(),
                eventType = AuditEventType.ERROR,
                entryId = "system",
                provider = provider,
                message = message,
                details = mapOf("error" to (error?.message ?: "unknown"))
            )
        )
    }

    fun clear() {
        logs.clear()
    }

    fun getLogCount(): Int = logs.size
}
