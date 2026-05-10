package com.cheroliv.graphify.apikey

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProviderEnumsTest {

    @Test
    fun `Provider enum should have 10 values`() {
        val providers = Provider.entries
        assertEquals(10, providers.size)
    }

    @Test
    fun `Provider should contain GOOGLE`() {
        assertTrue(Provider.entries.contains(Provider.GOOGLE))
    }

    @Test
    fun `Provider should contain HUGGINGFACE`() {
        assertTrue(Provider.entries.contains(Provider.HUGGINGFACE))
    }

    @Test
    fun `Provider should contain GROQ`() {
        assertTrue(Provider.entries.contains(Provider.GROQ))
    }

    @Test
    fun `Provider should contain OLLAMA`() {
        assertTrue(Provider.entries.contains(Provider.OLLAMA))
    }

    @Test
    fun `Provider should contain MISTRAL`() {
        assertTrue(Provider.entries.contains(Provider.MISTRAL))
    }

    @Test
    fun `Provider should contain GROK`() {
        assertTrue(Provider.entries.contains(Provider.GROK))
    }

    @Test
    fun `Provider should contain OPENAI`() {
        assertTrue(Provider.entries.contains(Provider.OPENAI))
    }

    @Test
    fun `Provider should contain ANTHROPIC`() {
        assertTrue(Provider.entries.contains(Provider.ANTHROPIC))
    }

    @Test
    fun `Provider should contain GITHUB`() {
        assertTrue(Provider.entries.contains(Provider.GITHUB))
    }

    @Test
    fun `Provider should contain UNKNOWN`() {
        assertTrue(Provider.entries.contains(Provider.UNKNOWN))
    }

    @Test
    fun `ServiceType enum should have 10 values`() {
        val services = ServiceType.entries
        assertEquals(10, services.size)
    }

    @Test
    fun `ServiceType should contain all expected values`() {
        val services = ServiceType.entries
        assertTrue(services.contains(ServiceType.TEXT_GENERATION))
        assertTrue(services.contains(ServiceType.IMAGE_GENERATION))
        assertTrue(services.contains(ServiceType.CODE_GENERATION))
        assertTrue(services.contains(ServiceType.EMBEDDINGS))
        assertTrue(services.contains(ServiceType.SPEECH_TO_TEXT))
        assertTrue(services.contains(ServiceType.TEXT_TO_SPEECH))
        assertTrue(services.contains(ServiceType.TRANSLATION))
        assertTrue(services.contains(ServiceType.CHAT_COMPLETION))
        assertTrue(services.contains(ServiceType.VISION))
        assertTrue(services.contains(ServiceType.CUSTOM))
    }

    @Test
    fun `RotationStrategy enum should have 4 values`() {
        val strategies = RotationStrategy.entries
        assertEquals(4, strategies.size)
    }

    @Test
    fun `RotationStrategy should contain all expected values`() {
        val strategies = RotationStrategy.entries
        assertTrue(strategies.contains(RotationStrategy.ROUND_ROBIN))
        assertTrue(strategies.contains(RotationStrategy.WEIGHTED))
        assertTrue(strategies.contains(RotationStrategy.LEAST_USED))
        assertTrue(strategies.contains(RotationStrategy.SMART))
    }

    @Test
    fun `Provider enum values should have correct names`() {
        assertEquals("GOOGLE", Provider.GOOGLE.name)
        assertEquals("HUGGINGFACE", Provider.HUGGINGFACE.name)
        assertEquals("GROQ", Provider.GROQ.name)
        assertEquals("OLLAMA", Provider.OLLAMA.name)
        assertEquals("MISTRAL", Provider.MISTRAL.name)
        assertEquals("GROK", Provider.GROK.name)
        assertEquals("OPENAI", Provider.OPENAI.name)
        assertEquals("ANTHROPIC", Provider.ANTHROPIC.name)
        assertEquals("GITHUB", Provider.GITHUB.name)
    }

    @Test
    fun `ServiceType enum values should have correct names`() {
        assertEquals("TEXT_GENERATION", ServiceType.TEXT_GENERATION.name)
        assertEquals("IMAGE_GENERATION", ServiceType.IMAGE_GENERATION.name)
        assertEquals("CODE_GENERATION", ServiceType.CODE_GENERATION.name)
        assertEquals("EMBEDDINGS", ServiceType.EMBEDDINGS.name)
        assertEquals("SPEECH_TO_TEXT", ServiceType.SPEECH_TO_TEXT.name)
        assertEquals("TEXT_TO_SPEECH", ServiceType.TEXT_TO_SPEECH.name)
        assertEquals("TRANSLATION", ServiceType.TRANSLATION.name)
        assertEquals("CHAT_COMPLETION", ServiceType.CHAT_COMPLETION.name)
        assertEquals("VISION", ServiceType.VISION.name)
        assertEquals("CUSTOM", ServiceType.CUSTOM.name)
    }

    @Test
    fun `RotationStrategy enum values should have correct names`() {
        assertEquals("ROUND_ROBIN", RotationStrategy.ROUND_ROBIN.name)
        assertEquals("WEIGHTED", RotationStrategy.WEIGHTED.name)
        assertEquals("LEAST_USED", RotationStrategy.LEAST_USED.name)
        assertEquals("SMART", RotationStrategy.SMART.name)
    }
}
