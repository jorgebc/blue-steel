package com.bluesteel.adapters.out.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MockNarrativeExtractionAdapterTest {

  private final MockNarrativeExtractionAdapter adapter = new MockNarrativeExtractionAdapter();

  @Test
  @DisplayName("should return exactly 2 actors in the canned result")
  void extract_returnsTwoActors() {
    var result = adapter.extract("raw text");

    assertThat(result.actors()).hasSize(2);
  }

  @Test
  @DisplayName("should return exactly 1 space in the canned result")
  void extract_returnsOneSpace() {
    var result = adapter.extract("raw text");

    assertThat(result.spaces()).hasSize(1);
  }

  @Test
  @DisplayName("should return exactly 1 event in the canned result")
  void extract_returnsOneEvent() {
    var result = adapter.extract("raw text");

    assertThat(result.events()).hasSize(1);
  }

  @Test
  @DisplayName("should return exactly 1 relation in the canned result")
  void extract_returnsOneRelation() {
    var result = adapter.extract("raw text");

    assertThat(result.relations()).hasSize(1);
  }

  @Test
  @DisplayName("should emit relation source/target endpoint mentions referencing the mock entities")
  void extract_relationCarriesEndpoints() {
    var result = adapter.extract("raw text");

    var relation = result.relations().get(0);
    assertThat(relation.sourceMention()).isEqualTo("Mira");
    assertThat(relation.targetMention()).isEqualTo("Thornwick");
  }

  @Test
  @DisplayName("should return a non-blank narrativeSummaryHeader")
  void extract_returnsNonBlankHeader() {
    var result = adapter.extract("raw text");

    assertThat(result.narrativeSummaryHeader()).isNotBlank();
  }
}
