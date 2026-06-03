package com.bluesteel.adapters.out.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiEmbeddingConnectionAutoConfiguration;
import org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiTextEmbeddingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Guards the {@code llm-real} embedding wiring (D-093). The Google GenAI embedding classes ship in
 * a separate {@code spring-ai-google-genai-embedding} artifact since Spring AI 2.0.x; without that
 * dependency the auto-configuration is gated off by {@code @ConditionalOnClass} and no {@link
 * EmbeddingModel} bean exists, which broke startup under {@code prod,llm-real}. Importing the
 * auto-configuration classes here also fails to compile if the dependency is dropped.
 */
@DisplayName("Google GenAI embedding auto-configuration")
class GoogleGenAiEmbeddingAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  GoogleGenAiEmbeddingConnectionAutoConfiguration.class,
                  GoogleGenAiTextEmbeddingAutoConfiguration.class));

  @Test
  @DisplayName("should expose a Google GenAI EmbeddingModel bean when the embedding api-key is set")
  void embeddingModel_withApiKey_isAutoConfigured() {
    contextRunner
        .withPropertyValues("spring.ai.google.genai.embedding.api-key=test-placeholder")
        .run(
            context ->
                assertThat(context)
                    .hasSingleBean(EmbeddingModel.class)
                    .getBean(EmbeddingModel.class)
                    .isInstanceOf(GoogleGenAiTextEmbeddingModel.class));
  }
}
