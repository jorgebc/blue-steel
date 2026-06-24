package com.bluesteel.application.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bluesteel.application.model.ingestion.ConflictWarning;
import com.bluesteel.application.model.ingestion.EntityContext;
import com.bluesteel.application.model.ingestion.ExtractedMention;
import com.bluesteel.application.model.ingestion.ExtractionResult;
import com.bluesteel.application.model.ingestion.ResolutionOutcome;
import com.bluesteel.application.model.ingestion.ResolvedEntity;
import com.bluesteel.application.model.ingestion.SimilarityResult;
import com.bluesteel.application.port.out.embedding.EmbeddingPort;
import com.bluesteel.application.port.out.ingestion.ConflictDetectionPort;
import com.bluesteel.application.port.out.ingestion.EntitySimilaritySearchPort;
import com.bluesteel.application.service.session.ConflictDetectionService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConflictDetectionService")
class ConflictDetectionServiceTest {

  private static final int CONTEXT_TOP_N = 5;

  @Mock private EmbeddingPort embeddingPort;
  @Mock private EntitySimilaritySearchPort entitySimilaritySearchPort;
  @Mock private ConflictDetectionPort conflictDetectionPort;

  private ConflictDetectionService sut;

  @BeforeEach
  void setUp() {
    sut =
        new ConflictDetectionService(
            embeddingPort, entitySimilaritySearchPort, conflictDetectionPort, CONTEXT_TOP_N);
  }

  @Test
  @DisplayName(
      "should return empty list without calling any port when no MATCH-resolved entities are present")
  void run_noMatchEntities_returnsEmptyListWithNoPortCalls() {
    UUID campaignId = UUID.randomUUID();
    ExtractedMention mention = new ExtractedMention("Thornwick", "A wizard", "the wizard");
    ExtractionResult extraction =
        new ExtractionResult("A quiet session.", List.of(mention), List.of(), List.of(), List.of());
    ResolvedEntity newEntity = new ResolvedEntity(mention, ResolutionOutcome.NEW, null);

    List<ConflictWarning> result = sut.run(campaignId, extraction, List.of(newEntity), "en");

    assertThat(result).isEmpty();
    verifyNoInteractions(embeddingPort, entitySimilaritySearchPort, conflictDetectionPort);
  }

  @Test
  @DisplayName("should return empty list without any port calls when resolved list is empty")
  void run_emptyResolvedList_returnsEmptyListWithNoPortCalls() {
    UUID campaignId = UUID.randomUUID();
    ExtractionResult extraction =
        new ExtractionResult("No entities.", List.of(), List.of(), List.of(), List.of());

    List<ConflictWarning> result = sut.run(campaignId, extraction, List.of(), "en");

    assertThat(result).isEmpty();
    verifyNoInteractions(embeddingPort, entitySimilaritySearchPort, conflictDetectionPort);
  }

  @Test
  @DisplayName(
      "should return empty list without port calls when resolved list contains only UNCERTAIN entities")
  void run_onlyUncertainEntities_returnsEmptyListWithNoPortCalls() {
    UUID campaignId = UUID.randomUUID();
    ExtractedMention mention = new ExtractedMention("Stranger", "Unknown", "a stranger");
    ExtractionResult extraction =
        new ExtractionResult(
            "Stranger appeared.", List.of(mention), List.of(), List.of(), List.of());
    ResolvedEntity uncertain = new ResolvedEntity(mention, ResolutionOutcome.UNCERTAIN, null);

    List<ConflictWarning> result = sut.run(campaignId, extraction, List.of(uncertain), "en");

    assertThat(result).isEmpty();
    verifyNoInteractions(embeddingPort, entitySimilaritySearchPort, conflictDetectionPort);
  }

  @Test
  @DisplayName(
      "should embed the narrative header and search all four entity types when MATCH entities are present")
  void run_withMatchEntity_embedsHeaderAndSearchesAllEntityTypes() {
    UUID campaignId = UUID.randomUUID();
    UUID existingEntityId = UUID.randomUUID();
    ExtractedMention mention = new ExtractedMention("Mira", "A scout", "the scout");
    ExtractionResult extraction =
        new ExtractionResult(
            "Mira led the assault.", List.of(mention), List.of(), List.of(), List.of());
    ResolvedEntity matchEntity =
        new ResolvedEntity(mention, ResolutionOutcome.MATCH, existingEntityId);

    float[] queryVector = {1.0f, 0.0f};
    when(embeddingPort.embed("Mira led the assault.")).thenReturn(queryVector);
    when(entitySimilaritySearchPort.search(queryVector, campaignId, "actor", CONTEXT_TOP_N))
        .thenReturn(List.of());
    when(entitySimilaritySearchPort.search(queryVector, campaignId, "space", CONTEXT_TOP_N))
        .thenReturn(List.of());
    when(entitySimilaritySearchPort.search(queryVector, campaignId, "event", CONTEXT_TOP_N))
        .thenReturn(List.of());
    when(entitySimilaritySearchPort.search(queryVector, campaignId, "relation", CONTEXT_TOP_N))
        .thenReturn(List.of());

    ArgumentCaptor<ExtractionResult> extractionCaptor =
        ArgumentCaptor.forClass(ExtractionResult.class);
    ArgumentCaptor<List<EntityContext>> contextCaptor =
        ArgumentCaptor.forClass((Class<List<EntityContext>>) (Class<?>) List.class);
    ArgumentCaptor<String> languageCaptor = ArgumentCaptor.forClass(String.class);
    when(conflictDetectionPort.detect(
            extractionCaptor.capture(), contextCaptor.capture(), languageCaptor.capture()))
        .thenReturn(List.of());

    List<ConflictWarning> result = sut.run(campaignId, extraction, List.of(matchEntity), "es");

    assertThat(result).isEmpty();
    assertThat(extractionCaptor.getValue()).isSameAs(extraction);
    assertThat(contextCaptor.getValue()).isEmpty();
    assertThat(languageCaptor.getValue()).isEqualTo("es");
  }

  @Test
  @DisplayName(
      "should map SimilarityResult fields to EntityContext and forward to ConflictDetectionPort")
  void run_withMatchEntity_mapsSimResultToEntityContextCorrectly() {
    UUID campaignId = UUID.randomUUID();
    UUID existingEntityId = UUID.randomUUID();
    UUID existingSessionId = UUID.randomUUID();
    ExtractedMention mention = new ExtractedMention("Aldric", "Warrior", "warrior Aldric");
    ExtractionResult extraction =
        new ExtractionResult("Aldric fell.", List.of(mention), List.of(), List.of(), List.of());
    ResolvedEntity matchEntity =
        new ResolvedEntity(mention, ResolutionOutcome.MATCH, existingEntityId);

    float[] queryVector = {0.5f};
    when(embeddingPort.embed("Aldric fell.")).thenReturn(queryVector);

    SimilarityResult candidate =
        new SimilarityResult(
            existingEntityId,
            "actor",
            "Aldric the Bold",
            "{\"alive\":true}",
            existingSessionId,
            3,
            0.9);
    when(entitySimilaritySearchPort.search(queryVector, campaignId, "actor", CONTEXT_TOP_N))
        .thenReturn(List.of(candidate));
    when(entitySimilaritySearchPort.search(queryVector, campaignId, "space", CONTEXT_TOP_N))
        .thenReturn(List.of());
    when(entitySimilaritySearchPort.search(queryVector, campaignId, "event", CONTEXT_TOP_N))
        .thenReturn(List.of());
    when(entitySimilaritySearchPort.search(queryVector, campaignId, "relation", CONTEXT_TOP_N))
        .thenReturn(List.of());

    ArgumentCaptor<ExtractionResult> extractionCaptor =
        ArgumentCaptor.forClass(ExtractionResult.class);
    ArgumentCaptor<List<EntityContext>> contextCaptor =
        ArgumentCaptor.forClass((Class<List<EntityContext>>) (Class<?>) List.class);
    ArgumentCaptor<String> languageCaptor = ArgumentCaptor.forClass(String.class);
    ConflictWarning warning = new ConflictWarning("Aldric", "Described as fallen but was alive.");
    when(conflictDetectionPort.detect(
            extractionCaptor.capture(), contextCaptor.capture(), languageCaptor.capture()))
        .thenReturn(List.of(warning));

    List<ConflictWarning> result = sut.run(campaignId, extraction, List.of(matchEntity), "es");

    assertThat(result).containsExactly(warning);
    assertThat(languageCaptor.getValue()).isEqualTo("es");

    List<EntityContext> capturedContext = contextCaptor.getValue();
    assertThat(capturedContext).hasSize(1);
    EntityContext ctx = capturedContext.get(0);
    assertThat(ctx.entityId()).isEqualTo(existingEntityId);
    assertThat(ctx.entityType()).isEqualTo("actor");
    assertThat(ctx.name()).isEqualTo("Aldric the Bold");
    assertThat(ctx.stateSnapshot()).isEqualTo("{\"alive\":true}");
    assertThat(ctx.sessionId()).isEqualTo(existingSessionId);
    assertThat(ctx.versionNumber()).isEqualTo(3);
  }
}
