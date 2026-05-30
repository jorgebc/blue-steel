package com.bluesteel.adapters.out.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;

@ExtendWith(MockitoExtension.class)
@DisplayName("SpringAiEmbeddingAdapter")
class SpringAiEmbeddingAdapterTest {

  @Mock private EmbeddingModel embeddingModel;

  private SpringAiEmbeddingAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new SpringAiEmbeddingAdapter(embeddingModel);
  }

  @Test
  @DisplayName("should delegate to EmbeddingModel and return its vector")
  void embed_delegatesToEmbeddingModelAndReturnsVector() {
    String content = "The heroes explored an ancient dungeon.";
    float[] expected = {0.1f, 0.2f, 0.3f};
    when(embeddingModel.embed(content)).thenReturn(expected);

    float[] result = adapter.embed(content);

    assertThat(result).isEqualTo(expected);
    verify(embeddingModel).embed(content);
  }

  @Test
  @DisplayName("should rethrow exception from EmbeddingModel without swallowing it")
  void embed_propagatesExceptionFromEmbeddingModel() {
    String content = "Some content.";
    when(embeddingModel.embed(content)).thenThrow(new RuntimeException("provider failure"));

    assertThatThrownBy(() -> adapter.embed(content))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("provider failure");
  }
}
