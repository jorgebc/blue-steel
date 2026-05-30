package com.bluesteel.application.model.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExtractionResultTest {

  private static final ExtractedMention MENTION =
      new ExtractedMention("Mira", "A wandering healer", "Mira appears from the forest");

  @Test
  @DisplayName("should construct with valid fields and expose them via accessors")
  void construct_validFields_accessorsWork() {
    var result =
        new ExtractionResult(
            "Session 1 summary", List.of(MENTION), List.of(), List.of(), List.of());

    assertThat(result.narrativeSummaryHeader()).isEqualTo("Session 1 summary");
    assertThat(result.actors()).containsExactly(MENTION);
    assertThat(result.spaces()).isEmpty();
    assertThat(result.events()).isEmpty();
    assertThat(result.relations()).isEmpty();
  }

  @Test
  @DisplayName("should reject null narrativeSummaryHeader")
  void construct_nullHeader_throwsNullPointerException() {
    assertThatThrownBy(() -> new ExtractionResult(null, List.of(), List.of(), List.of(), List.of()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("should reject blank narrativeSummaryHeader")
  void construct_blankHeader_throwsIllegalArgumentException() {
    assertThatThrownBy(
            () -> new ExtractionResult("   ", List.of(), List.of(), List.of(), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("should reject null actors list")
  void construct_nullActors_throwsNullPointerException() {
    assertThatThrownBy(() -> new ExtractionResult("header", null, List.of(), List.of(), List.of()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("should reject null spaces list")
  void construct_nullSpaces_throwsNullPointerException() {
    assertThatThrownBy(() -> new ExtractionResult("header", List.of(), null, List.of(), List.of()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("should reject null events list")
  void construct_nullEvents_throwsNullPointerException() {
    assertThatThrownBy(() -> new ExtractionResult("header", List.of(), List.of(), null, List.of()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("should reject null relations list")
  void construct_nullRelations_throwsNullPointerException() {
    assertThatThrownBy(() -> new ExtractionResult("header", List.of(), List.of(), List.of(), null))
        .isInstanceOf(NullPointerException.class);
  }
}
