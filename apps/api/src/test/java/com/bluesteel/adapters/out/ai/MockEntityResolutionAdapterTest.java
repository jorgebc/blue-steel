package com.bluesteel.adapters.out.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.bluesteel.application.model.ingestion.ExtractedMention;
import com.bluesteel.application.model.ingestion.ResolutionOutcome;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MockEntityResolutionAdapterTest {

  private final MockEntityResolutionAdapter adapter = new MockEntityResolutionAdapter();

  private static ExtractedMention mention(String name) {
    return new ExtractedMention(name, "description", "raw text");
  }

  @Test
  @DisplayName("should resolve 'Thornwick' to NEW with null matchedEntityId")
  void resolve_thornwick_returnsNew() {
    var results = adapter.resolve(List.of(mention("Thornwick")), List.of());

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().outcome()).isEqualTo(ResolutionOutcome.NEW);
    assertThat(results.getFirst().matchedEntityId()).isNull();
  }

  @Test
  @DisplayName("should resolve 'Stranger' to UNCERTAIN with null matchedEntityId")
  void resolve_stranger_returnsUncertain() {
    var results = adapter.resolve(List.of(mention("Stranger")), List.of());

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().outcome()).isEqualTo(ResolutionOutcome.UNCERTAIN);
    assertThat(results.getFirst().matchedEntityId()).isNull();
  }

  @Test
  @DisplayName("should resolve any unknown name to NEW by default")
  void resolve_unknownName_returnsNewByDefault() {
    var results = adapter.resolve(List.of(mention("Aldric")), List.of());

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().outcome()).isEqualTo(ResolutionOutcome.NEW);
    assertThat(results.getFirst().matchedEntityId()).isNull();
  }
}
