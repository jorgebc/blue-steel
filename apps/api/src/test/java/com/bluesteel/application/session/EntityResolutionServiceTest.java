package com.bluesteel.application.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bluesteel.application.model.ingestion.EntityContext;
import com.bluesteel.application.model.ingestion.ExtractedMention;
import com.bluesteel.application.model.ingestion.ExtractionResult;
import com.bluesteel.application.model.ingestion.ResolutionOutcome;
import com.bluesteel.application.model.ingestion.ResolvedEntity;
import com.bluesteel.application.model.ingestion.SimilarityResult;
import com.bluesteel.application.port.out.embedding.EmbeddingPort;
import com.bluesteel.application.port.out.ingestion.EntityResolutionPort;
import com.bluesteel.application.port.out.ingestion.EntitySimilaritySearchPort;
import com.bluesteel.application.service.session.EntityResolutionService;
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
@DisplayName("EntityResolutionService")
class EntityResolutionServiceTest {

  private static final double SIMILARITY_FLOOR = 0.75;
  private static final int TOP_N = 3;

  @Mock private EmbeddingPort embeddingPort;
  @Mock private EntitySimilaritySearchPort entitySimilaritySearchPort;
  @Mock private EntityResolutionPort entityResolutionPort;

  private EntityResolutionService sut;

  @BeforeEach
  void setUp() {
    sut =
        new EntityResolutionService(
            embeddingPort,
            entitySimilaritySearchPort,
            entityResolutionPort,
            SIMILARITY_FLOOR,
            TOP_N);
  }

  @Test
  @DisplayName(
      "should classify mention as NEW without calling EntityResolutionPort when max similarity is below the floor")
  void run_maxSimilarityBelowFloor_classifiesNewWithoutLlmCall() {
    UUID campaignId = UUID.randomUUID();
    ExtractedMention mention = new ExtractedMention("Thornwick", "A wizard", "the wizard");
    ExtractionResult extraction =
        new ExtractionResult("Header.", List.of(mention), List.of(), List.of(), List.of());

    float[] embedding = {1.0f, 0.0f};
    String mentionContent = "Thornwick A wizard";
    when(embeddingPort.embed(mentionContent)).thenReturn(embedding);

    // Below-floor candidate: similarity 0.5 < 0.75
    SimilarityResult lowCandidate =
        new SimilarityResult(UUID.randomUUID(), "actor", "Old Wizard", "{}", null, 1, 0.5);
    when(entitySimilaritySearchPort.search(embedding, campaignId, "actor", TOP_N))
        .thenReturn(List.of(lowCandidate));

    List<ResolvedEntity> results = sut.run(campaignId, extraction);

    assertThat(results).hasSize(1);
    assertThat(results.get(0).outcome()).isEqualTo(ResolutionOutcome.NEW);
    assertThat(results.get(0).mention()).isEqualTo(mention);
    assertThat(results.get(0).matchedEntityId()).isNull();

    // Stage 2 LLM port must not be called when below floor
    verifyNoInteractions(entityResolutionPort);
  }

  @Test
  @DisplayName(
      "should classify mention as NEW without calling EntityResolutionPort when no candidates are found")
  void run_noCandidates_classifiesNewWithoutLlmCall() {
    UUID campaignId = UUID.randomUUID();
    ExtractedMention mention = new ExtractedMention("Brand New Hero", "Unknown", "new hero");
    ExtractionResult extraction =
        new ExtractionResult("Header.", List.of(mention), List.of(), List.of(), List.of());

    float[] embedding = {0.0f, 1.0f};
    when(embeddingPort.embed("Brand New Hero Unknown")).thenReturn(embedding);
    when(entitySimilaritySearchPort.search(embedding, campaignId, "actor", TOP_N))
        .thenReturn(List.of());

    List<ResolvedEntity> results = sut.run(campaignId, extraction);

    assertThat(results).hasSize(1);
    assertThat(results.get(0).outcome()).isEqualTo(ResolutionOutcome.NEW);
    verifyNoInteractions(entityResolutionPort);
  }

  @Test
  @DisplayName(
      "should call EntityResolutionPort with top-N contexts when max similarity meets or exceeds the floor")
  void run_maxSimilarityAboveFloor_callsResolutionPortWithContexts() {
    UUID campaignId = UUID.randomUUID();
    UUID existingEntityId = UUID.randomUUID();
    UUID existingSessionId = UUID.randomUUID();
    ExtractedMention mention = new ExtractedMention("Mira", "A scout", "the scout");
    ExtractionResult extraction =
        new ExtractionResult("Header.", List.of(mention), List.of(), List.of(), List.of());

    float[] embedding = {1.0f, 0.0f};
    when(embeddingPort.embed("Mira A scout")).thenReturn(embedding);

    // Above-floor candidate: similarity 0.9 >= 0.75
    SimilarityResult highCandidate =
        new SimilarityResult(
            existingEntityId,
            "actor",
            "Mira Voss",
            "{\"role\":\"scout\"}",
            existingSessionId,
            2,
            0.9);
    when(entitySimilaritySearchPort.search(embedding, campaignId, "actor", TOP_N))
        .thenReturn(List.of(highCandidate));

    // The expected EntityContext built from the SimilarityResult by the service
    EntityContext expectedContext =
        new EntityContext(
            existingEntityId, "actor", "Mira Voss", "{\"role\":\"scout\"}", existingSessionId, 2);

    ResolvedEntity matchResult =
        new ResolvedEntity(mention, ResolutionOutcome.MATCH, existingEntityId);

    // Capture the contexts list forwarded to the port
    ArgumentCaptor<List<EntityContext>> contextsCaptor =
        ArgumentCaptor.forClass((Class<List<EntityContext>>) (Class<?>) List.class);
    ArgumentCaptor<List<ExtractedMention>> mentionsCaptor =
        ArgumentCaptor.forClass((Class<List<ExtractedMention>>) (Class<?>) List.class);

    when(entityResolutionPort.resolve(mentionsCaptor.capture(), contextsCaptor.capture()))
        .thenReturn(List.of(matchResult));

    List<ResolvedEntity> results = sut.run(campaignId, extraction);

    assertThat(results).hasSize(1);
    assertThat(results.get(0).outcome()).isEqualTo(ResolutionOutcome.MATCH);
    assertThat(results.get(0).matchedEntityId()).isEqualTo(existingEntityId);

    // Verify the mention was forwarded as a singleton list
    assertThat(mentionsCaptor.getValue()).containsExactly(mention);

    // Verify the SimilarityResult was correctly mapped to EntityContext
    assertThat(contextsCaptor.getValue()).containsExactly(expectedContext);
  }

  @Test
  @DisplayName(
      "should process all entity types (actors, spaces, events, relations) and aggregate results")
  void run_multipleEntityTypes_aggregatesAllResults() {
    UUID campaignId = UUID.randomUUID();
    ExtractedMention actor = new ExtractedMention("Aldric", "Warrior", "the warrior");
    ExtractedMention space = new ExtractedMention("Fortress", "A stronghold", "the fortress");
    ExtractionResult extraction =
        new ExtractionResult("Header.", List.of(actor), List.of(space), List.of(), List.of());

    float[] actorEmbedding = {1.0f, 0.0f};
    float[] spaceEmbedding = {0.0f, 1.0f};
    when(embeddingPort.embed("Aldric Warrior")).thenReturn(actorEmbedding);
    when(embeddingPort.embed("Fortress A stronghold")).thenReturn(spaceEmbedding);

    // Both below floor — both NEW
    when(entitySimilaritySearchPort.search(actorEmbedding, campaignId, "actor", TOP_N))
        .thenReturn(List.of());
    when(entitySimilaritySearchPort.search(spaceEmbedding, campaignId, "space", TOP_N))
        .thenReturn(List.of());

    List<ResolvedEntity> results = sut.run(campaignId, extraction);

    assertThat(results).hasSize(2);
    assertThat(results).allMatch(r -> r.outcome() == ResolutionOutcome.NEW);
  }
}
