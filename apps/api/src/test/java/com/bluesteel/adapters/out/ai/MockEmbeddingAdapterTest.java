package com.bluesteel.adapters.out.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MockEmbeddingAdapterTest {

  private static final int DIMENSION = 1024;

  private final MockEmbeddingAdapter adapter = new MockEmbeddingAdapter(DIMENSION);

  @Test
  @DisplayName("should return a vector sized to the configured embedding dimension")
  void embed_returnsVectorOfConfiguredDimension() {
    var vector = adapter.embed("some content");

    assertThat(vector).hasSize(DIMENSION);
  }

  @Test
  @DisplayName("should set index 0 to 1.0f")
  void embed_firstElementIsOne() {
    var vector = adapter.embed("some content");

    assertThat(vector[0]).isEqualTo(1.0f);
  }

  @Test
  @DisplayName("should set all remaining elements to 0.0f")
  void embed_remainingElementsAreZero() {
    var vector = adapter.embed("some content");

    for (int i = 1; i < vector.length; i++) {
      assertThat(vector[i]).isZero();
    }
  }
}
