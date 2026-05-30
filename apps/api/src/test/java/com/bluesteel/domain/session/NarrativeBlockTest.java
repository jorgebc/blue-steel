package com.bluesteel.domain.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NarrativeBlockTest {

  @Test
  @DisplayName("should create narrative block with valid inputs")
  void create_validInputs_succeeds() {
    UUID id = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();
    Instant now = Instant.now();

    NarrativeBlock block =
        NarrativeBlock.create(id, sessionId, "The party explored the dungeon.", 8, now);

    assertThat(block.id()).isEqualTo(id);
    assertThat(block.sessionId()).isEqualTo(sessionId);
    assertThat(block.rawSummaryText()).isEqualTo("The party explored the dungeon.");
    assertThat(block.tokenCount()).isEqualTo(8);
    assertThat(block.createdAt()).isEqualTo(now);
  }

  @Test
  @DisplayName("should reject blank rawSummaryText")
  void create_blankSummaryText_throws() {
    assertThatThrownBy(
            () ->
                NarrativeBlock.create(UUID.randomUUID(), UUID.randomUUID(), "  ", 0, Instant.now()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("rawSummaryText");
  }

  @Test
  @DisplayName("should reject null rawSummaryText")
  void create_nullSummaryText_throws() {
    assertThatThrownBy(
            () ->
                NarrativeBlock.create(UUID.randomUUID(), UUID.randomUUID(), null, 0, Instant.now()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("rawSummaryText");
  }

  @Test
  @DisplayName("should reject negative tokenCount")
  void create_negativeTokenCount_throws() {
    assertThatThrownBy(
            () ->
                NarrativeBlock.create(
                    UUID.randomUUID(), UUID.randomUUID(), "text", -1, Instant.now()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tokenCount");
  }

  @Test
  @DisplayName("should allow zero tokenCount")
  void create_zeroTokenCount_succeeds() {
    NarrativeBlock block =
        NarrativeBlock.create(UUID.randomUUID(), UUID.randomUUID(), "text", 0, Instant.now());
    assertThat(block.tokenCount()).isZero();
  }
}
