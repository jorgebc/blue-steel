package com.bluesteel.adapters.out.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bluesteel.application.model.ingestion.EntityContext;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("QueryPromptAssembler")
class QueryPromptAssemblerTest {

  private static final UUID SESSION_1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID SESSION_2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

  private static EntityContext context(UUID sessionId, String name, int version) {
    return new EntityContext(
        UUID.randomUUID(), "actor", name, name + " is a ranger.", sessionId, version);
  }

  @Test
  @DisplayName("should number each snapshot and label it with its session_id and version")
  void assemble_numbersSnapshotsAndLabelsWithSessionId() {
    QueryPromptAssembler assembler = new QueryPromptAssembler(6000);

    String prompt =
        assembler.assemble(
            "Who is Aragorn?",
            List.of(context(SESSION_1, "Aragorn", 3), context(SESSION_2, "Legolas", 1)));

    assertThat(prompt)
        .contains("[1] (session_id: " + SESSION_1 + ", sequence_number: 3)")
        .contains("actor \"Aragorn\": Aragorn is a ranger.")
        .contains("[2] (session_id: " + SESSION_2 + ", sequence_number: 1)")
        .contains("actor \"Legolas\": Legolas is a ranger.");
  }

  @Test
  @DisplayName("should include the citation-grounding rules and JSON response shape")
  void assemble_includesGroundingRulesAndJsonShape() {
    QueryPromptAssembler assembler = new QueryPromptAssembler(6000);

    String prompt =
        assembler.assemble("Who is Aragorn?", List.of(context(SESSION_1, "Aragorn", 1)));

    assertThat(prompt)
        .contains("ONLY")
        .contains("cite the session")
        .contains("Omit any claim you cannot ground")
        .contains("\"session_id\"")
        .contains("\"citations\"");
  }

  @Test
  @DisplayName("should render a no-context marker when context is empty")
  void assemble_emptyContext_rendersMarker() {
    QueryPromptAssembler assembler = new QueryPromptAssembler(6000);

    String prompt = assembler.assemble("Who is Aragorn?", List.of());

    assertThat(prompt).contains("(no world-state context is available)");
  }

  @Test
  @DisplayName("should throw TokenBudgetExceededException when prompt plus question exceeds budget")
  void assemble_overBudget_throwsTokenBudgetExceeded() {
    QueryPromptAssembler assembler = new QueryPromptAssembler(10);

    assertThatThrownBy(
            () -> assembler.assemble("Who is Aragorn?", List.of(context(SESSION_1, "Aragorn", 1))))
        .isInstanceOf(TokenBudgetExceededException.class)
        .hasMessageContaining("tokens");
  }
}
