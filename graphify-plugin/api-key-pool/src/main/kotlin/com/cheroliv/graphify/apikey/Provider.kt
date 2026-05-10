package com.cheroliv.graphify.apikey

enum class Provider {
    GOOGLE,
    HUGGINGFACE,
    GROQ,
    OLLAMA,
    MISTRAL,
    GROK,
    OPENAI,
    ANTHROPIC,
    GITHUB,
    UNKNOWN
}

enum class ServiceType {
    TEXT_GENERATION,
    IMAGE_GENERATION,
    CODE_GENERATION,
    EMBEDDINGS,
    SPEECH_TO_TEXT,
    TEXT_TO_SPEECH,
    TRANSLATION,
    CHAT_COMPLETION,
    VISION,
    CUSTOM
}

enum class RotationStrategy {
    ROUND_ROBIN,
    WEIGHTED,
    LEAST_USED,
    SMART
}
