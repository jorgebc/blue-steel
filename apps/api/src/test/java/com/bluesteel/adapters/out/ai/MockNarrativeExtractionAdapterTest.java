package com.bluesteel.adapters.out.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MockNarrativeExtractionAdapterTest {

  private final MockNarrativeExtractionAdapter adapter = new MockNarrativeExtractionAdapter();

  @Test
  @DisplayName("should return exactly 2 actors in the canned result")
  void extract_returnsTwoActors() {
    var result = adapter.extract("raw text", "en");

    assertThat(result.actors()).hasSize(2);
  }

  @Test
  @DisplayName("should return exactly 1 space in the canned result")
  void extract_returnsOneSpace() {
    var result = adapter.extract("raw text", "en");

    assertThat(result.spaces()).hasSize(1);
  }

  @Test
  @DisplayName("should return exactly 1 event in the canned result")
  void extract_returnsOneEvent() {
    var result = adapter.extract("raw text", "en");

    assertThat(result.events()).hasSize(1);
  }

  @Test
  @DisplayName("should return exactly 1 relation in the canned result")
  void extract_returnsOneRelation() {
    var result = adapter.extract("raw text", "en");

    assertThat(result.relations()).hasSize(1);
  }

  @Test
  @DisplayName("should emit relation source/target endpoint mentions referencing the mock entities")
  void extract_relationCarriesEndpoints() {
    var result = adapter.extract("raw text", "en");

    var relation = result.relations().get(0);
    assertThat(relation.sourceMention()).isEqualTo("Mira");
    assertThat(relation.targetMention()).isEqualTo("Thornwick");
  }

  @Test
  @DisplayName("should emit event space/actor mentions and type referencing the mock entities")
  void extract_eventCarriesLinks() {
    var result = adapter.extract("raw text", "en");

    var event = result.events().get(0);
    assertThat(event.spaceMention()).isEqualTo("Thornwick");
    assertThat(event.involvedActorMentions()).containsExactly("Mira", "Aldric");
    assertThat(event.eventType()).isEqualTo("arrival");
  }

  @Test
  @DisplayName("should return a non-blank narrativeSummaryHeader")
  void extract_returnsNonBlankHeader() {
    var result = adapter.extract("raw text", "en");

    assertThat(result.narrativeSummaryHeader()).isNotBlank();
  }

  @Test
  @DisplayName("should return the English canned summary header for the en content language")
  void extract_enLanguage_returnsEnglishHeader() {
    var result = adapter.extract("raw text", "en");

    assertThat(result.narrativeSummaryHeader())
        .isEqualTo("The party encountered Mira and ventured into Thornwick.");
  }

  @Test
  @DisplayName(
      "should honor the es content language by returning Spanish descriptions while keeping entity structure (D-103)")
  void extract_esLanguage_returnsSpanishContent() {
    var result = adapter.extract("raw text", "es");

    // Structure unchanged: same entity counts, proper-noun names, and event/relation links.
    assertThat(result.actors()).hasSize(2);
    assertThat(result.spaces()).hasSize(1);
    assertThat(result.actors().get(0).name()).isEqualTo("Mira");
    assertThat(result.relations().get(0).sourceMention()).isEqualTo("Mira");
    assertThat(result.relations().get(0).targetMention()).isEqualTo("Thornwick");

    // Content is Spanish, not the English canned text.
    assertThat(result.narrativeSummaryHeader())
        .isEqualTo("El grupo se encontró con Mira y se adentró en Thornwick.");
    assertThat(result.actors().get(0).description())
        .isEqualTo("Una sanadora errante conocida por el grupo");
  }
}
