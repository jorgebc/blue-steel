package com.bluesteel.application.port.out.embedding;

/**
 * Driven port: generates a dense vector embedding for a text string. Used for both entity
 * resolution (pgvector similarity search, Stage 1) and async post-commit embedding generation
 * (D-063). Dimension is profile-dependent (1536 for {@code llm-real}, 1024 for {@code llm-ollama}).
 */
public interface EmbeddingPort {

  float[] embed(String content);
}
