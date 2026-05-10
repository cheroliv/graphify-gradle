package com.cheroliv.graphify.apikey

class ApiKeyPool(
    private val entries: List<ApiKeyEntry>,
    private val rotationStrategy: RotationStrategy = RotationStrategy.ROUND_ROBIN,
    private val fallbackEnabled: Boolean = true,
    autoResetEnabled: Boolean = true,
    auditEnabled: Boolean = true
) {
    private var currentIndex = 0
    private val tracker: QuotaTracker = QuotaTracker()
    private val resetManager: QuotaResetManager = QuotaResetManager(tracker, autoResetEnabled)
    private val auditLogger: QuotaAuditLogger = QuotaAuditLogger(auditEnabled)

    init {
        entries.forEach { entry ->
            tracker.getUsage(entry.id)
        }
    }

    fun getNextKey(): ApiKeyEntry {
        if (entries.isEmpty()) {
            throw IllegalStateException("API Key Pool is empty")
        }

        val selectedEntry = when (rotationStrategy) {
            RotationStrategy.ROUND_ROBIN -> getNextRoundRobin()
            RotationStrategy.LEAST_USED -> getNextLeastUsed()
            RotationStrategy.WEIGHTED, RotationStrategy.SMART -> getNextRoundRobin()
        }

        tracker.trackUsage(selectedEntry.id)
        val usageCount = tracker.getUsage(selectedEntry.id)
        auditLogger.logUsage(selectedEntry, usageCount)

        if (tracker.isQuotaExceeded(selectedEntry)) {
            auditLogger.logQuotaExceeded(selectedEntry, usageCount)
            if (resetManager.checkAndReset(selectedEntry)) {
                auditLogger.logReset(selectedEntry.id, resetManager.getResetCount(selectedEntry.id), false)
            }
        }

        return selectedEntry
    }

    private fun getNextRoundRobin(): ApiKeyEntry {
        val entry = entries[currentIndex % entries.size]
        currentIndex = (currentIndex + 1) % entries.size
        return entry
    }

    private fun getNextLeastUsed(): ApiKeyEntry {
        return entries.minByOrNull { entry ->
            tracker.getUsage(entry.id)
        } ?: entries.first()
    }

    fun isQuotaExceeded(entry: ApiKeyEntry): Boolean {
        return tracker.isQuotaExceeded(entry)
    }

    fun getAllKeys(): List<ApiKeyEntry> = entries

    fun size(): Int = entries.size

    fun isFallbackEnabled(): Boolean = fallbackEnabled

    fun resetUsageCounts() {
        tracker.resetAll()
    }

    fun getUsageCount(entryId: String): Long {
        return tracker.getUsage(entryId)
    }

    fun getTracker(): QuotaTracker = tracker

    fun getResetManager(): QuotaResetManager = resetManager

    fun getAuditLogger(): QuotaAuditLogger = auditLogger

    fun getUsagePercentage(entry: ApiKeyEntry): Double {
        return tracker.getUsagePercentage(entry)
    }

    fun manualReset(entryId: String) {
        resetManager.manualReset(entryId)
        auditLogger.logReset(entryId, resetManager.getResetCount(entryId), true)
    }

    fun getAuditLogs(): List<AuditLogEntry> = auditLogger.getLogs()
}
