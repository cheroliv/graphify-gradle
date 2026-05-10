package com.cheroliv.graphify.apikey

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class ApiKeyPoolTest {

    private fun createTestEntry(
        id: String = "test-key",
        email: String = "test@example.com",
        name: String = "Test Key",
        quota: QuotaConfig = QuotaConfig()
    ): ApiKeyEntry {
        return ApiKeyEntry(
            id = id,
            email = email,
            name = name,
            keyRef = "TEST_KEY_REF",
            provider = Provider.GOOGLE,
            services = listOf(ServiceType.CHAT_COMPLETION),
            quota = quota
        )
    }

    @Test
    fun `should create pool with entries`() {
        val entries = listOf(
            createTestEntry("key1"),
            createTestEntry("key2")
        )
        val pool = ApiKeyPool(entries)

        assertEquals(2, pool.size())
        assertEquals(entries, pool.getAllKeys())
    }

    @Test
    fun `should throw exception when pool is empty`() {
        val pool = ApiKeyPool(emptyList())

        assertFailsWith<IllegalStateException> {
            pool.getNextKey()
        }
    }

    @Test
    fun `should rotate keys in round robin fashion`() {
        val entries = listOf(
            createTestEntry("key1"),
            createTestEntry("key2"),
            createTestEntry("key3")
        )
        val pool = ApiKeyPool(entries, RotationStrategy.ROUND_ROBIN)

        val first = pool.getNextKey()
        val second = pool.getNextKey()
        val third = pool.getNextKey()
        val fourth = pool.getNextKey()

        assertEquals("key1", first.id)
        assertEquals("key2", second.id)
        assertEquals("key3", third.id)
        assertEquals("key1", fourth.id)
    }

    @Test
    fun `should return least used key with LEAST_USED strategy`() {
        val entries = listOf(
            createTestEntry("key1"),
            createTestEntry("key2"),
            createTestEntry("key3")
        )
        val pool = ApiKeyPool(entries, RotationStrategy.LEAST_USED)

        val first = pool.getNextKey()
        val second = pool.getNextKey()

        assertEquals("key1", first.id)
        assertEquals("key2", second.id)
    }

    @Test
    fun `should track usage counts`() {
        val entries = listOf(
            createTestEntry("key1"),
            createTestEntry("key2")
        )
        val pool = ApiKeyPool(entries)

        pool.getNextKey()
        pool.getNextKey()
        pool.getNextKey()

        assertEquals(2, pool.getUsageCount("key1"))
        assertEquals(1, pool.getUsageCount("key2"))
    }

    @Test
    fun `should reset usage counts`() {
        val entries = listOf(
            createTestEntry("key1"),
            createTestEntry("key2")
        )
        val pool = ApiKeyPool(entries)

        pool.getNextKey()
        pool.getNextKey()
        pool.resetUsageCounts()

        assertEquals(0, pool.getUsageCount("key1"))
        assertEquals(0, pool.getUsageCount("key2"))
    }

    @Test
    fun `should detect quota exceeded`() {
        val quota = QuotaConfig(
            limitValue = 100,
            thresholdPercent = 80
        )
        val entries = listOf(
            createTestEntry("key1", quota = quota)
        )
        val pool = ApiKeyPool(entries, autoResetEnabled = false)

        assertFalse(pool.isQuotaExceeded(entries[0]))

        for (i in 1..80) {
            pool.getNextKey()
        }

        assertTrue(pool.isQuotaExceeded(entries[0]))
        assertEquals(80, pool.getUsageCount("key1"))
    }

    @Test
    fun `should respect custom threshold percent`() {
        val quota = QuotaConfig(
            limitValue = 100,
            thresholdPercent = 50
        )
        val entries = listOf(
            createTestEntry("key1", quota = quota)
        )
        val pool = ApiKeyPool(entries, autoResetEnabled = false)

        for (i in 1..49) {
            pool.getNextKey()
        }
        assertFalse(pool.isQuotaExceeded(entries[0]))

        pool.getNextKey()
        assertTrue(pool.isQuotaExceeded(entries[0]))
        assertEquals(50, pool.getUsageCount("key1"))
    }

    @Test
    fun `should have fallback enabled by default`() {
        val pool = ApiKeyPool(listOf(createTestEntry()))
        assertTrue(pool.isFallbackEnabled())
    }

    @Test
    fun `should respect fallback disabled setting`() {
        val pool = ApiKeyPool(
            entries = listOf(createTestEntry()),
            fallbackEnabled = false
        )
        assertFalse(pool.isFallbackEnabled())
    }

    @Test
    fun `should perform automatic reset when quota exceeded`() {
        val quota = QuotaConfig(
            limitValue = 100,
            thresholdPercent = 80,
            resetPolicy = ResetPolicy.DAILY
        )
        val entries = listOf(
            createTestEntry("key1", quota = quota)
        )
        val pool = ApiKeyPool(entries, autoResetEnabled = true)

        for (i in 1..80) {
            pool.getNextKey()
        }

        assertEquals(0, pool.getUsageCount("key1"))
        assertEquals(1, pool.getResetManager().getResetCount("key1"))
    }

    @Test
    fun `should log audit events when using keys`() {
        val entries = listOf(createTestEntry("key1"))
        val pool = ApiKeyPool(entries, auditEnabled = true)

        pool.getNextKey()
        pool.getNextKey()

        val logs = pool.getAuditLogs()
        assertEquals(2, logs.size)
        assertEquals(AuditEventType.USAGE, logs[0].eventType)
        assertEquals(AuditEventType.USAGE, logs[1].eventType)
    }

    @Test
    fun `should log quota exceeded and reset events`() {
        val quota = QuotaConfig(
            limitValue = 100,
            thresholdPercent = 80,
            resetPolicy = ResetPolicy.DAILY
        )
        val entries = listOf(
            createTestEntry("key1", quota = quota)
        )
        val pool = ApiKeyPool(entries, autoResetEnabled = true, auditEnabled = true)

        for (i in 1..80) {
            pool.getNextKey()
        }

        val logs = pool.getAuditLogs()
        assertTrue(logs.size >= 2)
        assertTrue(logs.any { it.eventType == AuditEventType.QUOTA_EXCEEDED })
        assertTrue(logs.any { it.eventType == AuditEventType.AUTO_RESET })
    }
}
