package com.cheroliv.graphify.apikey

class QuotaTracker {

    private val usageCounts = mutableMapOf<String, Long>()

    fun trackUsage(entryId: String) {
        usageCounts[entryId] = (usageCounts[entryId] ?: 0) + 1
    }

    fun getUsage(entryId: String): Long {
        return usageCounts[entryId] ?: 0
    }

    fun isQuotaExceeded(entry: ApiKeyEntry): Boolean {
        val quota = entry.quota
        val currentUsage = usageCounts[entry.id] ?: 0
        val threshold = (quota.limitValue * quota.thresholdPercent) / 100
        return currentUsage >= threshold
    }

    fun reset(entryId: String) {
        usageCounts[entryId] = 0
    }

    fun resetAll() {
        usageCounts.clear()
    }

    fun getUsagePercentage(entry: ApiKeyEntry): Double {
        val currentUsage = usageCounts[entry.id] ?: 0
        val percentage = (currentUsage.toDouble() / entry.quota.limitValue) * 100.0
        return percentage.coerceIn(0.0, 100.0)
    }
}
