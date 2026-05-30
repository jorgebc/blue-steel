package com.bluesteel.adapters.out.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.bluesteel.TestcontainersPostgresBaseIT;
import com.bluesteel.application.port.out.embedding.EmbeddingPort;
import com.bluesteel.application.port.out.ingestion.ConflictDetectionPort;
import com.bluesteel.application.port.out.ingestion.EntityResolutionPort;
import com.bluesteel.application.port.out.ingestion.NarrativeExtractionPort;
import com.bluesteel.application.port.out.query.QueryAnsweringPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Verifies that all five AI port interfaces resolve to their {@code Mock*} implementations when the
 * {@code local} Spring context starts (no real LLM provider profile active).
 */
class AiAdapterWiringIT extends TestcontainersPostgresBaseIT {

  @Autowired private NarrativeExtractionPort narrativeExtractionPort;
  @Autowired private EntityResolutionPort entityResolutionPort;
  @Autowired private ConflictDetectionPort conflictDetectionPort;
  @Autowired private EmbeddingPort embeddingPort;
  @Autowired private QueryAnsweringPort queryAnsweringPort;

  @Test
  @DisplayName(
      "should wire NarrativeExtractionPort to MockNarrativeExtractionAdapter under local profile")
  void narrativeExtractionPort_resolvesToMockAdapter() {
    assertThat(narrativeExtractionPort).isInstanceOf(MockNarrativeExtractionAdapter.class);
  }

  @Test
  @DisplayName(
      "should wire EntityResolutionPort to MockEntityResolutionAdapter under local profile")
  void entityResolutionPort_resolvesToMockAdapter() {
    assertThat(entityResolutionPort).isInstanceOf(MockEntityResolutionAdapter.class);
  }

  @Test
  @DisplayName(
      "should wire ConflictDetectionPort to MockConflictDetectionAdapter under local profile")
  void conflictDetectionPort_resolvesToMockAdapter() {
    assertThat(conflictDetectionPort).isInstanceOf(MockConflictDetectionAdapter.class);
  }

  @Test
  @DisplayName("should wire EmbeddingPort to MockEmbeddingAdapter under local profile")
  void embeddingPort_resolvesToMockAdapter() {
    assertThat(embeddingPort).isInstanceOf(MockEmbeddingAdapter.class);
  }

  @Test
  @DisplayName("should wire QueryAnsweringPort to MockQueryAnsweringAdapter under local profile")
  void queryAnsweringPort_resolvesToMockAdapter() {
    assertThat(queryAnsweringPort).isInstanceOf(MockQueryAnsweringAdapter.class);
  }
}
