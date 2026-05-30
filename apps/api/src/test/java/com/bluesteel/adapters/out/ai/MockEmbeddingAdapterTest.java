package com.bluesteel.adapters.out.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MockEmbeddingAdapterTest {

  private final MockEmbeddingAdapter adapter = new MockEmbeddingAdapter();

  @Test
  @DisplayName("should return a vector of length 1536")
  void embed_returnsVectorOfCorrectDimension() {
    var vector = adapter.embed("some content");

    assertThat(vector).hasSize(MockEmbeddingAdapter.EMBEDDING_DIMENSION);
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
