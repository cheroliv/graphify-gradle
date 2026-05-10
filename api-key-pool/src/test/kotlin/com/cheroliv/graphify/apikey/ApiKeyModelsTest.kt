package com.cheroliv.graphify.apikey

import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull

class ApiKeyModelsTest {

    @Test
    fun `QuotaConfig should have correct defaults`() {
        val config = QuotaConfig()
        assertEquals(QuotaType.REQUESTS, config.limitType)
        assertEquals(1000, config.limitValue)
        assertEquals(0, config.consumedValue)
        assertEquals(80, config.thresholdPercent)
        assertEquals(ResetPolicy.DAILY, config.resetPolicy)
        assertNull(config.periodStart)
        assertNull(config.periodEnd)
        assertNull(config.lastManualSync)
    }

    @Test
    fun `QuotaConfig should be customizable`() {
        val now = LocalDateTime.now()
        val config = QuotaConfig(
            limitType = QuotaType.TOKENS,
            limitValue = 50000,
            consumedValue = 12000,
            thresholdPercent = 90,
            periodStart = now,
            periodEnd = now.plusDays(1),
            resetPolicy = ResetPolicy.WEEKLY,
            lastManualSync = now.minusHours(1)
        )
        assertEquals(QuotaType.TOKENS, config.limitType)
        assertEquals(50000, config.limitValue)
        assertEquals(12000, config.consumedValue)
        assertEquals(90, config.thresholdPercent)
        assertEquals(ResetPolicy.WEEKLY, config.resetPolicy)
    }

    @Test
    fun `ApiKeyEntry should be instantiable with minimal params`() {
        val entry = ApiKeyEntry(
            id = "key-001",
            email = "test@example.com",
            name = "Test Key",
            keyRef = "GOOGLE_API_KEY_1",
            provider = Provider.GOOGLE,
            services = emptyList()
        )
        assertEquals("key-001", entry.id)
        assertEquals("test@example.com", entry.email)
        assertEquals("Test Key", entry.name)
        assertEquals("GOOGLE_API_KEY_1", entry.keyRef)
        assertEquals(Provider.GOOGLE, entry.provider)
        assertTrue(entry.services.isEmpty())
        assertNull(entry.expirationDate)
        assertEquals(QuotaConfig(), entry.quota)
        assertTrue(entry.metadata.isEmpty())
    }

    @Test
    fun `ApiKeyEntry should support all services`() {
        val entry = ApiKeyEntry(
            id = "key-002",
            email = "multi@example.com",
            name = "Multi-Service Key",
            keyRef = "HF_API_KEY_1",
            provider = Provider.HUGGINGFACE,
            services = listOf(
                ServiceType.TEXT_GENERATION,
                ServiceType.IMAGE_GENERATION,
                ServiceType.EMBEDDINGS
            )
        )
        assertEquals(3, entry.services.size)
        assertTrue(entry.services.contains(ServiceType.TEXT_GENERATION))
        assertTrue(entry.services.contains(ServiceType.IMAGE_GENERATION))
        assertTrue(entry.services.contains(ServiceType.EMBEDDINGS))
    }

    @Test
    fun `ApiKeyEntry should support expiration and metadata`() {
        val expiration = LocalDateTime.of(2026, 12, 31, 23, 59)
        val metadata = mapOf(
            "createdAt" to "2024-01-01",
            "purpose" to "testing",
            "owner" to "dev-team"
        )
        val entry = ApiKeyEntry(
            id = "key-003",
            email = "expiring@example.com",
            name = "Expiring Key",
            keyRef = "OPENAI_API_KEY_1",
            provider = Provider.OPENAI,
            services = emptyList(),
            expirationDate = expiration,
            metadata = metadata
        )
        assertEquals(expiration, entry.expirationDate)
        assertEquals(3, entry.metadata.size)
        assertEquals("testing", entry.metadata["purpose"])
    }

    @Test
    fun `ApiKeyPoolConfig should have correct defaults`() {
        val config = ApiKeyPoolConfig()
        assertEquals("1.0", config.version)
        assertEquals("default", config.poolName)
        assertEquals(RotationStrategy.ROUND_ROBIN, config.rotationStrategy)
        assertTrue(config.fallbackEnabled)
        assertTrue(config.auditEnabled)
        assertTrue(config.providers.isEmpty())
    }

    @Test
    fun `ApiKeyPoolConfig should support custom configuration`() {
        val googleKeys = listOf(
            ApiKeyEntry("g1", "g1@test.com", "Google #1", "GOOGLE_KEY_1", Provider.GOOGLE, emptyList()),
            ApiKeyEntry("g2", "g2@test.com", "Google #2", "GOOGLE_KEY_2", Provider.GOOGLE, emptyList())
        )
        val config = ApiKeyPoolConfig(
            version = "2.0",
            poolName = "production-pool",
            rotationStrategy = RotationStrategy.LEAST_USED,
            fallbackEnabled = false,
            auditEnabled = false,
            providers = mapOf(Provider.GOOGLE to googleKeys)
        )
        assertEquals("2.0", config.version)
        assertEquals("production-pool", config.poolName)
        assertEquals(RotationStrategy.LEAST_USED, config.rotationStrategy)
        assertEquals(1, config.providers.size)
        assertEquals(2, config.providers[Provider.GOOGLE]?.size)
    }

    @Test
    fun `QuotaType enum should have all values`() {
        val types = QuotaType.entries
        assertEquals(8, types.size)
        assertTrue(types.contains(QuotaType.REQUESTS))
        assertTrue(types.contains(QuotaType.TOKENS))
        assertTrue(types.contains(QuotaType.DAILY))
        assertTrue(types.contains(QuotaType.HOURLY))
        assertTrue(types.contains(QuotaType.MINUTE))
        assertTrue(types.contains(QuotaType.MONTHLY))
        assertTrue(types.contains(QuotaType.WEEKLY))
        assertTrue(types.contains(QuotaType.CUSTOM))
    }

    @Test
    fun `ResetPolicy enum should have all values`() {
        val policies = ResetPolicy.entries
        assertEquals(5, policies.size)
        assertTrue(policies.contains(ResetPolicy.DAILY))
        assertTrue(policies.contains(ResetPolicy.WEEKLY))
        assertTrue(policies.contains(ResetPolicy.MONTHLY))
        assertTrue(policies.contains(ResetPolicy.NEVER))
        assertTrue(policies.contains(ResetPolicy.MANUAL))
    }
}
