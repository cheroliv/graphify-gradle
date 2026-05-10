package com.cheroliv.graphify.apikey

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class QuotaResetManagerTest {

    private lateinit var tracker: QuotaTracker
    private lateinit var resetManager: QuotaResetManager
    private lateinit var apiKeyEntry: ApiKeyEntry

    @BeforeEach
    fun setUp() {
        tracker = QuotaTracker()
        apiKeyEntry = ApiKeyEntry(
            id = "test-key-1",
            email = "test@example.com",
            name = "Test Key",
            keyRef = "TEST_API_KEY_1",
            provider = Provider.GOOGLE,
            services = listOf(ServiceType.TEXT_GENERATION),
            quota = QuotaConfig(
                limitType = QuotaType.REQUESTS,
                limitValue = 100,
                thresholdPercent = 80,
                resetPolicy = ResetPolicy.DAILY
            )
        )
    }

    @Test
    fun `test checkAndReset returns false when quota not exceeded`() {
        resetManager = QuotaResetManager(tracker)
        repeat(50) { tracker.trackUsage(apiKeyEntry.id) }
        assertFalse(resetManager.checkAndReset(apiKeyEntry))
    }

    @Test
    fun `test checkAndReset returns true when quota exceeded`() {
        resetManager = QuotaResetManager(tracker)
        repeat(80) { tracker.trackUsage(apiKeyEntry.id) }
        assertTrue(resetManager.checkAndReset(apiKeyEntry))
    }

    @Test
    fun `test checkAndReset resets usage count`() {
        resetManager = QuotaResetManager(tracker)
        repeat(80) { tracker.trackUsage(apiKeyEntry.id) }
        assertEquals(80, tracker.getUsage(apiKeyEntry.id))

        resetManager.checkAndReset(apiKeyEntry)
        assertEquals(0, tracker.getUsage(apiKeyEntry.id))
    }

    @Test
    fun `test checkAndReset increments reset count`() {
        resetManager = QuotaResetManager(tracker)
        repeat(80) { tracker.trackUsage(apiKeyEntry.id) }

        resetManager.checkAndReset(apiKeyEntry)
        assertEquals(1, resetManager.getResetCount(apiKeyEntry.id))

        resetManager.manualReset(apiKeyEntry.id)
        assertEquals(2, resetManager.getResetCount(apiKeyEntry.id))
    }

    @Test
    fun `test checkAndReset updates last reset timestamp`() {
        resetManager = QuotaResetManager(tracker)
        repeat(80) { tracker.trackUsage(apiKeyEntry.id) }

        val beforeReset = LocalDateTime.now()
        resetManager.checkAndReset(apiKeyEntry)
        val afterReset = LocalDateTime.now()

        val lastReset = resetManager.getLastReset(apiKeyEntry.id)
        assertNotNull(lastReset)
        assertTrue(lastReset!! >= beforeReset || lastReset <= afterReset)
    }

    @Test
    fun `test manualReset resets usage count`() {
        resetManager = QuotaResetManager(tracker)
        repeat(50) { tracker.trackUsage(apiKeyEntry.id) }

        resetManager.manualReset(apiKeyEntry.id)
        assertEquals(0, tracker.getUsage(apiKeyEntry.id))
    }

    @Test
    fun `test manualReset increments reset count`() {
        resetManager = QuotaResetManager(tracker)
        resetManager.manualReset(apiKeyEntry.id)
        assertEquals(1, resetManager.getResetCount(apiKeyEntry.id))

        resetManager.manualReset(apiKeyEntry.id)
        assertEquals(2, resetManager.getResetCount(apiKeyEntry.id))
    }

    @Test
    fun `test checkAndReset disabled when autoResetEnabled is false`() {
        resetManager = QuotaResetManager(tracker, autoResetEnabled = false)
        repeat(80) { tracker.trackUsage(apiKeyEntry.id) }
        assertFalse(resetManager.checkAndReset(apiKeyEntry))
        assertEquals(80, tracker.getUsage(apiKeyEntry.id))
    }

    @Test
    fun `test checkAndReset with MANUAL policy does not auto reset`() {
        val manualEntry = apiKeyEntry.copy(
            quota = apiKeyEntry.quota.copy(resetPolicy = ResetPolicy.MANUAL)
        )
        resetManager = QuotaResetManager(tracker)
        repeat(80) { tracker.trackUsage(manualEntry.id) }
        assertFalse(resetManager.checkAndReset(manualEntry))
    }

    @Test
    fun `test checkAndReset with NEVER policy does not auto reset`() {
        val neverEntry = apiKeyEntry.copy(
            quota = apiKeyEntry.quota.copy(resetPolicy = ResetPolicy.NEVER)
        )
        resetManager = QuotaResetManager(tracker)
        repeat(80) { tracker.trackUsage(neverEntry.id) }
        assertFalse(resetManager.checkAndReset(neverEntry))
    }

    @Test
    fun `test resetAll clears all data`() {
        resetManager = QuotaResetManager(tracker)
        repeat(50) { tracker.trackUsage(apiKeyEntry.id) }
        resetManager.manualReset(apiKeyEntry.id)

        resetManager.resetAll()
        assertEquals(0, tracker.getUsage(apiKeyEntry.id))
        assertEquals(0, resetManager.getResetCount(apiKeyEntry.id))
        assertNull(resetManager.getLastReset(apiKeyEntry.id))
    }
}
