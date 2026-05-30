package com.bluesteel.application.model.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResolvedEntityTest {

  private static final ExtractedMention MENTION =
      new ExtractedMention("Mira", "A wandering healer", "Mira appears");

  @Test
  @DisplayName("should carry a non-null matchedEntityId for MATCH outcome")
  void construct_matchOutcome_hasMatchedEntityId() {
    var entityId = UUID.randomUUID();
    var resolved = new ResolvedEntity(MENTION, ResolutionOutcome.MATCH, entityId);

    assertThat(resolved.outcome()).isEqualTo(ResolutionOutcome.MATCH);
    assertThat(resolved.matchedEntityId()).isEqualTo(entityId);
    assertThat(resolved.mention()).isEqualTo(MENTION);
  }

  @Test
  @DisplayName("should allow null matchedEntityId for NEW outcome")
  void construct_newOutcome_nullMatchedEntityId() {
    var resolved = new ResolvedEntity(MENTION, ResolutionOutcome.NEW, null);

    assertThat(resolved.outcome()).isEqualTo(ResolutionOutcome.NEW);
    assertThat(resolved.matchedEntityId()).isNull();
  }

  @Test
  @DisplayName("should allow null matchedEntityId for UNCERTAIN outcome")
  void construct_uncertainOutcome_nullMatchedEntityId() {
    var resolved = new ResolvedEntity(MENTION, ResolutionOutcome.UNCERTAIN, null);

    assertThat(resolved.outcome()).isEqualTo(ResolutionOutcome.UNCERTAIN);
    assertThat(resolved.matchedEntityId()).isNull();
  }
}
