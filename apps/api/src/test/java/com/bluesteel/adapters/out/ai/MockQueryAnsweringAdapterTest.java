package com.bluesteel.adapters.out.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MockQueryAnsweringAdapterTest {

  private final MockQueryAnsweringAdapter adapter = new MockQueryAnsweringAdapter();

  @Test
  @DisplayName("should return a non-blank answer")
  void answer_returnsNonBlankAnswer() {
    var response = adapter.answer("Who is Mira?", List.of(), "en");

    assertThat(response.answer()).isNotBlank();
  }

  @Test
  @DisplayName("should return exactly one citation")
  void answer_returnsExactlyOneCitation() {
    var response = adapter.answer("Who is Mira?", List.of(), "en");

    assertThat(response.citations()).hasSize(1);
  }

  @Test
  @DisplayName("should return a citation with sequenceNumber 1")
  void answer_citationHasSequenceNumberOne() {
    var response = adapter.answer("Who is Mira?", List.of(), "en");

    assertThat(response.citations().getFirst().sequenceNumber()).isEqualTo(1);
  }

  @Test
  @DisplayName("should answer in Spanish for the es content language, keeping one citation (D-103)")
  void answer_esLanguage_returnsSpanishAnswer() {
    var response = adapter.answer("¿Quién es Mira?", List.of(), "es");

    assertThat(response.answer()).isEqualTo("Esta es una respuesta simulada a: ¿Quién es Mira?");
    assertThat(response.citations()).hasSize(1);
    assertThat(response.citations().getFirst().sequenceNumber()).isEqualTo(1);
  }

  @Test
  @DisplayName("should answer in English for the en content language (D-103)")
  void answer_enLanguage_returnsEnglishAnswer() {
    var response = adapter.answer("Who is Mira?", List.of(), "en");

    assertThat(response.answer()).isEqualTo("This is a mock answer to: Who is Mira?");
  }
}
