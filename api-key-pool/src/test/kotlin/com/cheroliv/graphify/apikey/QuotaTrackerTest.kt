package com.cheroliv.graphify.apikey

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class QuotaTrackerTest {

    private lateinit var tracker: QuotaTracker
    private lateinit var apiKeyEntry: ApiKeyEntry

    @BeforeEach
    fun setUp() {
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
                thresholdPercent = 80
            )
        )
        tracker = QuotaTracker()
    }

    @Test
    fun `test initial usage is zero`() {
        assertEquals(0, tracker.getUsage(apiKeyEntry.id))
    }

    @Test
    fun `test track usage increments counter`() {
        tracker.trackUsage(apiKeyEntry.id)
        assertEquals(1, tracker.getUsage(apiKeyEntry.id))

        tracker.trackUsage(apiKeyEntry.id)
        tracker.trackUsage(apiKeyEntry.id)
        assertEquals(3, tracker.getUsage(apiKeyEntry.id))
    }

    @Test
    fun `test isQuotaExceeded returns false when under threshold`() {
        repeat(79) { tracker.trackUsage(apiKeyEntry.id) }
        assertFalse(tracker.isQuotaExceeded(apiKeyEntry))
    }

    @Test
    fun `test isQuotaExceeded returns true when threshold reached`() {
        repeat(80) { tracker.trackUsage(apiKeyEntry.id) }
        assertTrue(tracker.isQuotaExceeded(apiKeyEntry))
    }

    @Test
    fun `test isQuotaExceeded with custom threshold`() {
        val customEntry = apiKeyEntry.copy(
            quota = apiKeyEntry.quota.copy(thresholdPercent = 50)
        )
        repeat(50) { tracker.trackUsage(customEntry.id) }
        assertTrue(tracker.isQuotaExceeded(customEntry))
    }

    @Test
    fun `test reset resets usage to zero`() {
        repeat(50) { tracker.trackUsage(apiKeyEntry.id) }
        assertEquals(50, tracker.getUsage(apiKeyEntry.id))

        tracker.reset(apiKeyEntry.id)
        assertEquals(0, tracker.getUsage(apiKeyEntry.id))
    }

    @Test
    fun `test resetAll resets all tracked usages`() {
        val entry2 = ApiKeyEntry(
            id = "test-key-2",
            email = "test2@example.com",
            name = "Test Key 2",
            keyRef = "TEST_API_KEY_2",
            provider = Provider.MISTRAL,
            services = listOf(ServiceType.TEXT_GENERATION)
        )

        tracker.trackUsage(apiKeyEntry.id)
        tracker.trackUsage(apiKeyEntry.id)
        tracker.trackUsage(entry2.id)

        assertEquals(2, tracker.getUsage(apiKeyEntry.id))
        assertEquals(1, tracker.getUsage(entry2.id))

        tracker.resetAll()
        assertEquals(0, tracker.getUsage(apiKeyEntry.id))
        assertEquals(0, tracker.getUsage(entry2.id))
    }

    @Test
    fun `test getUsagePercentage returns correct percentage`() {
        repeat(25) { tracker.trackUsage(apiKeyEntry.id) }
        assertEquals(25.0, tracker.getUsagePercentage(apiKeyEntry), 0.01)
    }

    @Test
    fun `test getUsagePercentage caps at 100`() {
        repeat(150) { tracker.trackUsage(apiKeyEntry.id) }
        assertEquals(100.0, tracker.getUsagePercentage(apiKeyEntry), 0.01)
    }

    @Test
    fun `test track usage for multiple keys independently`() {
        val entry2 = ApiKeyEntry(
            id = "test-key-2",
            email = "test2@example.com",
            name = "Test Key 2",
            keyRef = "TEST_API_KEY_2",
            provider = Provider.MISTRAL,
            services = listOf(ServiceType.TEXT_GENERATION)
        )

        tracker.trackUsage(apiKeyEntry.id)
        tracker.trackUsage(apiKeyEntry.id)
        tracker.trackUsage(entry2.id)

        assertEquals(2, tracker.getUsage(apiKeyEntry.id))
        assertEquals(1, tracker.getUsage(entry2.id))
    }

    @Test
    fun `test isQuotaExceeded for unknown key returns false`() {
        val unknownEntry = ApiKeyEntry(
            id = "unknown-key",
            email = "unknown@example.com",
            name = "Unknown Key",
            keyRef = "UNKNOWN_KEY",
            provider = Provider.GROQ,
            services = listOf(ServiceType.TEXT_GENERATION)
        )
        assertFalse(tracker.isQuotaExceeded(unknownEntry))
    }
}
