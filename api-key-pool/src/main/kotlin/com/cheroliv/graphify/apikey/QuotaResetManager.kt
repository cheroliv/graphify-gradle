package com.cheroliv.graphify.apikey

import java.time.LocalDateTime

class QuotaResetManager(
    private val tracker: QuotaTracker,
    private val autoResetEnabled: Boolean = true
) {
    private val resetTimestamps = mutableMapOf<String, LocalDateTime>()
    private val resetCounts = mutableMapOf<String, Int>()

    fun checkAndReset(entry: ApiKeyEntry): Boolean {
        if (!autoResetEnabled) {
            return false
        }

        if (!tracker.isQuotaExceeded(entry)) {
            return false
        }

        when (entry.quota.resetPolicy) {
            ResetPolicy.DAILY -> {
                if (shouldResetDaily(entry.id)) {
                    performReset(entry.id)
                    return true
                }
            }
            ResetPolicy.WEEKLY -> {
                if (shouldResetWeekly(entry.id)) {
                    performReset(entry.id)
                    return true
                }
            }
            ResetPolicy.MONTHLY -> {
                if (shouldResetMonthly(entry.id)) {
                    performReset(entry.id)
                    return true
                }
            }
            ResetPolicy.MANUAL -> {
                return false
            }
            ResetPolicy.NEVER -> {
                return false
            }
        }

        return false
    }

    fun manualReset(entryId: String) {
        tracker.reset(entryId)
        resetTimestamps[entryId] = LocalDateTime.now()
        resetCounts[entryId] = (resetCounts[entryId] ?: 0) + 1
    }

    fun getResetCount(entryId: String): Int {
        return resetCounts[entryId] ?: 0
    }

    fun getLastReset(entryId: String): LocalDateTime? {
        return resetTimestamps[entryId]
    }

    private fun shouldResetDaily(entryId: String): Boolean {
        val lastReset = resetTimestamps[entryId]
        return lastReset == null || lastReset.toLocalDate() < LocalDateTime.now().toLocalDate()
    }

    private fun shouldResetWeekly(entryId: String): Boolean {
        val lastReset = resetTimestamps[entryId]
        if (lastReset == null) return true

        val currentWeek = LocalDateTime.now().toLocalDate().atStartOfDay()
            .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val lastResetWeek = lastReset.toLocalDate().atStartOfDay()
            .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))

        return currentWeek > lastResetWeek
    }

    private fun shouldResetMonthly(entryId: String): Boolean {
        val lastReset = resetTimestamps[entryId]
        return lastReset == null ||
            lastReset.monthValue < LocalDateTime.now().monthValue ||
            lastReset.year < LocalDateTime.now().year
    }

    private fun performReset(entryId: String) {
        tracker.reset(entryId)
        resetTimestamps[entryId] = LocalDateTime.now()
        resetCounts[entryId] = (resetCounts[entryId] ?: 0) + 1
    }

    fun resetAll() {
        tracker.resetAll()
        resetTimestamps.clear()
        resetCounts.clear()
    }
}
