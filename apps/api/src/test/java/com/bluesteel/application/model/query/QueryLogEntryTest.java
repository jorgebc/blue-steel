package com.bluesteel.application.model.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("QueryLogEntry")
class QueryLogEntryTest {

  private static final UUID ID = UUID.randomUUID();
  private static final UUID CAMPAIGN_ID = UUID.randomUUID();
  private static final UUID ASKER_ID = UUID.randomUUID();
  private static final Instant CREATED_AT = Instant.now();

  @Test
  @DisplayName("should construct with all fields when question and answer are non-blank")
  void construct_validFields_succeeds() {
    Citation citation = new Citation(UUID.randomUUID(), 1, "Mira appears.");

    QueryLogEntry entry =
        new QueryLogEntry(
            ID,
            CAMPAIGN_ID,
            ASKER_ID,
            "Who is Mira?",
            "Mira is a rogue.",
            List.of(citation),
            CREATED_AT);

    assertThat(entry.question()).isEqualTo("Who is Mira?");
    assertThat(entry.answer()).isEqualTo("Mira is a rogue.");
    assertThat(entry.citations()).containsExactly(citation);
  }

  @Test
  @DisplayName("should throw IllegalArgumentException when question is null")
  void construct_nullQuestion_throws() {
    assertThatThrownBy(
            () ->
                new QueryLogEntry(ID, CAMPAIGN_ID, ASKER_ID, null, "answer", List.of(), CREATED_AT))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("should throw IllegalArgumentException when question is blank")
  void construct_blankQuestion_throws() {
    assertThatThrownBy(
            () ->
                new QueryLogEntry(
                    ID, CAMPAIGN_ID, ASKER_ID, "   ", "answer", List.of(), CREATED_AT))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("should throw IllegalArgumentException when answer is null")
  void construct_nullAnswer_throws() {
    assertThatThrownBy(
            () ->
                new QueryLogEntry(
                    ID, CAMPAIGN_ID, ASKER_ID, "question", null, List.of(), CREATED_AT))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("should throw IllegalArgumentException when answer is blank")
  void construct_blankAnswer_throws() {
    assertThatThrownBy(
            () ->
                new QueryLogEntry(
                    ID, CAMPAIGN_ID, ASKER_ID, "question", "  ", List.of(), CREATED_AT))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("should default null citations to an empty list")
  void construct_nullCitations_defaultsToEmpty() {
    QueryLogEntry entry =
        new QueryLogEntry(ID, CAMPAIGN_ID, ASKER_ID, "question", "answer", null, CREATED_AT);

    assertThat(entry.citations()).isEmpty();
  }

  @Test
  @DisplayName("should hold an immutable copy of the citations list")
  void construct_citations_isImmutableCopy() {
    List<Citation> mutable = new ArrayList<>();
    mutable.add(new Citation(UUID.randomUUID(), 1, "snippet"));

    QueryLogEntry entry =
        new QueryLogEntry(ID, CAMPAIGN_ID, ASKER_ID, "question", "answer", mutable, CREATED_AT);
    mutable.clear();

    assertThat(entry.citations()).hasSize(1);
    assertThatThrownBy(() -> entry.citations().add(new Citation(UUID.randomUUID(), 2, "x")))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
