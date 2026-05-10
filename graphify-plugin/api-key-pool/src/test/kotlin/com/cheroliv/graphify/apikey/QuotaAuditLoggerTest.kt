package com.cheroliv.graphify.apikey

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class QuotaAuditLoggerTest {

    private lateinit var logger: QuotaAuditLogger
    private lateinit var apiKeyEntry: ApiKeyEntry

    @BeforeEach
    fun setUp() {
        logger = QuotaAuditLogger()
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
    }

    @Test
    fun `test logUsage adds entry to logs`() {
        logger.logUsage(apiKeyEntry, 1)
        assertEquals(1, logger.getLogCount())
        assertEquals(AuditEventType.USAGE, logger.getLogs().first().eventType)
    }

    @Test
    fun `test logQuotaExceeded adds entry to logs`() {
        logger.logQuotaExceeded(apiKeyEntry, 80)
        assertEquals(1, logger.getLogCount())
        assertEquals(AuditEventType.QUOTA_EXCEEDED, logger.getLogs().first().eventType)
    }

    @Test
    fun `test logReset with manual adds entry to logs`() {
        logger.logReset(apiKeyEntry.id, 1, isManual = true)
        assertEquals(1, logger.getLogCount())
        assertEquals(AuditEventType.MANUAL_RESET, logger.getLogs().first().eventType)
    }

    @Test
    fun `test logReset with auto adds entry to logs`() {
        logger.logReset(apiKeyEntry.id, 1, isManual = false)
        assertEquals(1, logger.getLogCount())
        assertEquals(AuditEventType.AUTO_RESET, logger.getLogs().first().eventType)
    }

    @Test
    fun `test logRotation adds entry to logs`() {
        val toEntry = ApiKeyEntry(
            id = "test-key-2",
            email = "test2@example.com",
            name = "Test Key 2",
            keyRef = "TEST_API_KEY_2",
            provider = Provider.MISTRAL,
            services = listOf(ServiceType.TEXT_GENERATION)
        )
        logger.logRotation(apiKeyEntry, toEntry, "quota_exceeded")
        assertEquals(1, logger.getLogCount())
        assertEquals(AuditEventType.ROTATION, logger.getLogs().first().eventType)
    }

    @Test
    fun `test getLogsForEntry filters by entry ID`() {
        logger.logUsage(apiKeyEntry, 1)
        logger.logUsage(apiKeyEntry, 2)

        val otherEntry = ApiKeyEntry(
            id = "test-key-2",
            email = "test2@example.com",
            name = "Test Key 2",
            keyRef = "TEST_API_KEY_2",
            provider = Provider.MISTRAL,
            services = listOf(ServiceType.TEXT_GENERATION)
        )
        logger.logUsage(otherEntry, 1)

        assertEquals(2, logger.getLogsForEntry(apiKeyEntry.id).size)
        assertEquals(1, logger.getLogsForEntry(otherEntry.id).size)
    }

    @Test
    fun `test getLogsByType filters by event type`() {
        logger.logUsage(apiKeyEntry, 1)
        logger.logUsage(apiKeyEntry, 2)
        logger.logQuotaExceeded(apiKeyEntry, 80)

        assertEquals(2, logger.getLogsByType(AuditEventType.USAGE).size)
        assertEquals(1, logger.getLogsByType(AuditEventType.QUOTA_EXCEEDED).size)
        assertEquals(0, logger.getLogsByType(AuditEventType.ROTATION).size)
    }

    @Test
    fun `test clear removes all logs`() {
        logger.logUsage(apiKeyEntry, 1)
        logger.logUsage(apiKeyEntry, 2)
        assertEquals(2, logger.getLogCount())

        logger.clear()
        assertEquals(0, logger.getLogCount())
    }

    @Test
    fun `test logging disabled when enabled is false`() {
        val disabledLogger = QuotaAuditLogger(enabled = false)
        disabledLogger.logUsage(apiKeyEntry, 1)
        disabledLogger.logQuotaExceeded(apiKeyEntry, 80)
        disabledLogger.logReset(apiKeyEntry.id, 1, true)
        assertEquals(0, disabledLogger.getLogCount())
    }

    @Test
    fun `test log entry contains correct details`() {
        logger.logUsage(apiKeyEntry, 42)
        val entry = logger.getLogs().first()

        assertEquals(apiKeyEntry.id, entry.entryId)
        assertEquals(Provider.GOOGLE, entry.provider)
        assertTrue(entry.message.contains("42"))
        assertEquals("42", entry.details["usageCount"])
        assertEquals("100", entry.details["limitValue"])
        assertEquals("80", entry.details["thresholdPercent"])
    }

    @Test
    fun `test multiple log entries maintain order`() {
        logger.logUsage(apiKeyEntry, 1)
        logger.logQuotaExceeded(apiKeyEntry, 80)
        logger.logReset(apiKeyEntry.id, 1, false)

        val logs = logger.getLogs()
        assertEquals(AuditEventType.USAGE, logs[0].eventType)
        assertEquals(AuditEventType.QUOTA_EXCEEDED, logs[1].eventType)
        assertEquals(AuditEventType.AUTO_RESET, logs[2].eventType)
    }
}
