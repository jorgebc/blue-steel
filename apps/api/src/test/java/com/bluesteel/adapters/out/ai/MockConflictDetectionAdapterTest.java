package com.bluesteel.adapters.out.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.bluesteel.application.model.ingestion.ExtractedMention;
import com.bluesteel.application.model.ingestion.ExtractionResult;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MockConflictDetectionAdapterTest {

  private final MockConflictDetectionAdapter adapter = new MockConflictDetectionAdapter();

  private static ExtractionResult minimalResult() {
    var mention = new ExtractedMention("Mira", "healer", "raw text");
    return new ExtractionResult("Summary", List.of(mention), List.of(), List.of(), List.of());
  }

  @Test
  @DisplayName("should return exactly one ConflictWarning on the first call")
  void detect_firstCall_returnsOneWarning() {
    var warnings = adapter.detect(minimalResult(), List.of(), "en");

    assertThat(warnings).hasSize(1);
    assertThat(warnings.getFirst().entityName()).isEqualTo("Mira");
  }

  @Test
  @DisplayName("should return an empty list on the second call")
  void detect_secondCall_returnsEmpty() {
    adapter.detect(minimalResult(), List.of(), "en"); // first call
    var warnings = adapter.detect(minimalResult(), List.of(), "en"); // second call

    assertThat(warnings).isEmpty();
  }

  @Test
  @DisplayName("should return an English conflict description for the en content language")
  void detect_enLanguage_returnsEnglishDescription() {
    var warnings = adapter.detect(minimalResult(), List.of(), "en");

    assertThat(warnings.getFirst().description())
        .isEqualTo(
            "Previously described as a healer; now referred to as a warrior — possible continuity conflict.");
  }

  @Test
  @DisplayName(
      "should honor the es content language by returning a Spanish conflict description (D-103)")
  void detect_esLanguage_returnsSpanishDescription() {
    var warnings = adapter.detect(minimalResult(), List.of(), "es");

    assertThat(warnings).hasSize(1);
    assertThat(warnings.getFirst().entityName()).isEqualTo("Mira");
    assertThat(warnings.getFirst().description())
        .isEqualTo(
            "Antes descrita como sanadora; ahora se la menciona como guerrera — posible conflicto de continuidad.");
  }
}
