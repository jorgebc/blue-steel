package com.bluesteel.adapters.out.ai;

import org.springframework.context.annotation.Configuration;

/**
 * Configuration home for AI adapter beans (D-039).
 *
 * <p>Three-way provider profile split:
 *
 * <ul>
 *   <li><b>mock</b> ({@code !llm-real & !llm-ollama}): {@code Mock*} adapters — deterministic
 *       canned responses, zero API cost. Active by default in {@code local} dev and CI.
 *   <li><b>llm-real</b>: Google Gemini via Spring AI {@code ChatClient} + {@code EmbeddingModel}.
 *       Bean wiring deferred to F2.4/F2.6 to avoid requiring API keys at local startup.
 *   <li><b>llm-ollama</b>: Local Ollama models via Spring AI. Bean wiring deferred to F2.12.
 * </ul>
 *
 * <p>Real {@code ChatClient} and {@code EmbeddingModel} beans will be defined here once F2.4 /
 * F2.12 are implemented.
 */
@Configuration
public class AiConfig {}
