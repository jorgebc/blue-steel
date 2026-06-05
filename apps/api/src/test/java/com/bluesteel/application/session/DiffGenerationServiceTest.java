package com.bluesteel.application.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.bluesteel.application.model.ingestion.ConflictWarning;
import com.bluesteel.application.model.ingestion.ExtractedMention;
import com.bluesteel.application.model.ingestion.ExtractedRelation;
import com.bluesteel.application.model.ingestion.ExtractionResult;
import com.bluesteel.application.model.ingestion.ResolutionOutcome;
import com.bluesteel.application.model.ingestion.ResolvedEntity;
import com.bluesteel.application.model.session.ConflictCard;
import com.bluesteel.application.model.session.DiffCard;
import com.bluesteel.application.model.session.DiffPayload;
import com.bluesteel.application.model.session.ExistingEntityCard;
import com.bluesteel.application.model.session.NewEntityCard;
import com.bluesteel.application.model.session.UncertainEntityCard;
import com.bluesteel.application.port.out.session.SessionRepository;
import com.bluesteel.application.service.session.DiffGenerationService;
import com.bluesteel.domain.session.Session;
import com.bluesteel.domain.session.SessionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
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
@DisplayName("DiffGenerationService")
class DiffGenerationServiceTest {

  @Mock private SessionRepository sessionRepository;

  private ObjectMapper objectMapper;
  private DiffGenerationService sut;

  private static final UUID SESSION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID CAMPAIGN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID OWNER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID MATCHED_ENTITY_ID =
      UUID.fromString("44444444-4444-4444-4444-444444444444");

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    sut = new DiffGenerationService(sessionRepository, objectMapper);
  }

  private Session processingSession() {
    Session s = Session.create(SESSION_ID, CAMPAIGN_ID, OWNER_ID, Instant.now());
    s.startProcessing();
    return s;
  }

  @Test
  @DisplayName("should build ExistingEntityCard for a MATCH actor and transition session to DRAFT")
  void run_withMatchActor_buildsExistingEntityCardAndTransitionsToDraft() throws Exception {
    ExtractedMention mention = new ExtractedMention("Mira", "A brave warrior", "Mira appeared.");
    ExtractionResult extraction =
        new ExtractionResult(
            "Heroes stormed the fortress.", List.of(mention), List.of(), List.of(), List.of());
    ResolvedEntity resolved =
        new ResolvedEntity(mention, ResolutionOutcome.MATCH, MATCHED_ENTITY_ID);
    Session session = processingSession();

    sut.run(session, extraction, List.of(resolved), List.of());

    ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
    verify(sessionRepository).save(captor.capture());
    Session saved = captor.getValue();
    assertThat(saved.status()).isEqualTo(SessionStatus.DRAFT);

    DiffPayload payload = objectMapper.readValue(saved.diffPayload(), DiffPayload.class);
    assertThat(payload.actors()).hasSize(1);
    DiffCard card = payload.actors().get(0);
    assertThat(card).isInstanceOf(ExistingEntityCard.class);
    ExistingEntityCard existing = (ExistingEntityCard) card;
    assertThat(existing.entityId()).isEqualTo(MATCHED_ENTITY_ID);
    assertThat(existing.entityType()).isEqualTo("actor");
    assertThat(existing.name()).isEqualTo("Mira");
    assertThat(existing.changedFields()).containsKey("description");
  }

  @Test
  @DisplayName("should build NewEntityCard for a NEW space")
  void run_withNewSpace_buildsNewEntityCard() throws Exception {
    ExtractedMention mention =
        new ExtractedMention(
            "The Dark Fortress", "A forbidding stronghold", "They entered the fortress.");
    ExtractionResult extraction =
        new ExtractionResult(
            "The fortress loomed.", List.of(), List.of(mention), List.of(), List.of());
    ResolvedEntity resolved = new ResolvedEntity(mention, ResolutionOutcome.NEW, null);
    Session session = processingSession();

    sut.run(session, extraction, List.of(resolved), List.of());

    ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
    verify(sessionRepository).save(captor.capture());
    DiffPayload payload =
        objectMapper.readValue(captor.getValue().diffPayload(), DiffPayload.class);
    assertThat(payload.spaces()).hasSize(1);
    DiffCard card = payload.spaces().get(0);
    assertThat(card).isInstanceOf(NewEntityCard.class);
    NewEntityCard newCard = (NewEntityCard) card;
    assertThat(newCard.entityType()).isEqualTo("space");
    assertThat(newCard.name()).isEqualTo("The Dark Fortress");
    assertThat(newCard.fullProfile()).containsKey("description");
  }

  @Test
  @DisplayName(
      "should build UncertainEntityCard with null candidateEntityId for an UNCERTAIN event")
  void run_withUncertainEvent_buildsUncertainEntityCard() throws Exception {
    ExtractedMention mention =
        new ExtractedMention("The Battle", "A clash of forces", "The battle raged.");
    ExtractionResult extraction =
        new ExtractionResult(
            "The battle was fierce.", List.of(), List.of(), List.of(mention), List.of());
    ResolvedEntity resolved = new ResolvedEntity(mention, ResolutionOutcome.UNCERTAIN, null);
    Session session = processingSession();

    sut.run(session, extraction, List.of(resolved), List.of());

    ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
    verify(sessionRepository).save(captor.capture());
    DiffPayload payload =
        objectMapper.readValue(captor.getValue().diffPayload(), DiffPayload.class);
    assertThat(payload.events()).hasSize(1);
    DiffCard card = payload.events().get(0);
    assertThat(card).isInstanceOf(UncertainEntityCard.class);
    UncertainEntityCard uncertain = (UncertainEntityCard) card;
    assertThat(uncertain.entityType()).isEqualTo("event");
    assertThat(uncertain.extractedMention()).isEqualTo("The Battle");
    assertThat(uncertain.candidateEntityId()).isNull();
  }

  @Test
  @DisplayName(
      "should build ConflictCard with derived entityId and entityType for a matching ConflictWarning")
  void run_withConflictWarningMatchingActor_buildsConflictCard() throws Exception {
    ExtractedMention mention = new ExtractedMention("Mira", "Now deceased", "Mira died.");
    ExtractionResult extraction =
        new ExtractionResult(
            "Mira met her end.", List.of(mention), List.of(), List.of(), List.of());
    ResolvedEntity resolved =
        new ResolvedEntity(mention, ResolutionOutcome.MATCH, MATCHED_ENTITY_ID);
    ConflictWarning warning =
        new ConflictWarning("Mira", "Mira is described as dead but world state says alive.");
    Session session = processingSession();

    sut.run(session, extraction, List.of(resolved), List.of(warning));

    ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
    verify(sessionRepository).save(captor.capture());
    DiffPayload payload =
        objectMapper.readValue(captor.getValue().diffPayload(), DiffPayload.class);
    assertThat(payload.detectedConflicts()).hasSize(1);
    ConflictCard conflict = payload.detectedConflicts().get(0);
    assertThat(conflict.entityId()).isEqualTo(MATCHED_ENTITY_ID);
    assertThat(conflict.entityType()).isEqualTo("actor");
    assertThat(conflict.description())
        .isEqualTo("Mira is described as dead but world state says alive.");
    assertThat(conflict.extractedFact())
        .isEqualTo("Mira is described as dead but world state says alive.");
    assertThat(conflict.existingFact())
        .isEqualTo("Mira is described as dead but world state says alive.");
  }

  @Test
  @DisplayName("should omit description key from snapshot when LLM returns null description (FU5)")
  void run_withNullDescription_buildsSnapshotWithoutDescriptionKey() throws Exception {
    ExtractedMention mention = new ExtractedMention("Mira", null, "Mira appeared.");
    ExtractionResult extraction =
        new ExtractionResult("Summary.", List.of(mention), List.of(), List.of(), List.of());
    ResolvedEntity resolved = new ResolvedEntity(mention, ResolutionOutcome.NEW, null);
    Session session = processingSession();

    sut.run(session, extraction, List.of(resolved), List.of());

    ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
    verify(sessionRepository).save(captor.capture());
    DiffPayload payload =
        objectMapper.readValue(captor.getValue().diffPayload(), DiffPayload.class);
    NewEntityCard card = (NewEntityCard) payload.actors().get(0);
    assertThat(card.fullProfile()).doesNotContainKey("description");
    assertThat(card.fullProfile()).containsKey("name");
  }

  @Test
  @DisplayName("should include kind in relation snapshot when LLM provides it (FU2)")
  void run_withRelationKind_stashesKindInSnapshot() throws Exception {
    ExtractedRelation relation =
        new ExtractedRelation(
            "Mira guides the party",
            "A bond of trust",
            "alliance",
            "Mira",
            "Thornwick",
            "Mira led them.");
    ExtractionResult extraction =
        new ExtractionResult("Summary.", List.of(), List.of(), List.of(), List.of(relation));
    ResolvedEntity resolved = new ResolvedEntity(relation.toMention(), ResolutionOutcome.NEW, null);
    Session session = processingSession();

    sut.run(session, extraction, List.of(resolved), List.of());

    ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
    verify(sessionRepository).save(captor.capture());
    DiffPayload payload =
        objectMapper.readValue(captor.getValue().diffPayload(), DiffPayload.class);
    NewEntityCard card = (NewEntityCard) payload.relations().get(0);
    assertThat(card.fullProfile()).containsEntry("kind", "alliance");
  }

  @Test
  @DisplayName("should omit kind from relation snapshot when LLM does not provide it (FU2)")
  void run_withNullRelationKind_omitsKindFromSnapshot() throws Exception {
    ExtractedRelation relation =
        new ExtractedRelation(
            "Mira guides the party", "A bond of trust", null, "Mira", "Thornwick", "Mira led.");
    ExtractionResult extraction =
        new ExtractionResult("Summary.", List.of(), List.of(), List.of(), List.of(relation));
    ResolvedEntity resolved = new ResolvedEntity(relation.toMention(), ResolutionOutcome.NEW, null);
    Session session = processingSession();

    sut.run(session, extraction, List.of(resolved), List.of());

    ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
    verify(sessionRepository).save(captor.capture());
    DiffPayload payload =
        objectMapper.readValue(captor.getValue().diffPayload(), DiffPayload.class);
    NewEntityCard card = (NewEntityCard) payload.relations().get(0);
    assertThat(card.fullProfile()).doesNotContainKey("kind");
  }

  @Test
  @DisplayName("should mark session FAILED and rethrow when serialization fails")
  void run_whenSerializationFails_marksSessionFailedAndRethrows() throws Exception {
    ObjectMapper spyMapper = spy(new ObjectMapper());
    DiffGenerationService failingSut = new DiffGenerationService(sessionRepository, spyMapper);
    // DiffPayload is built internally with random UUIDs — match by type (TEST-02 exception for
    // unknowable args)
    doThrow(new RuntimeException("Serialization failure"))
        .when(spyMapper)
        .writeValueAsString(any(DiffPayload.class));

    ExtractedMention mention = new ExtractedMention("Mira", "A warrior", "Mira fought.");
    ExtractionResult extraction =
        new ExtractionResult(
            "Mira fought bravely.", List.of(mention), List.of(), List.of(), List.of());
    ResolvedEntity resolved = new ResolvedEntity(mention, ResolutionOutcome.NEW, null);
    Session session = processingSession();

    assertThatThrownBy(() -> failingSut.run(session, extraction, List.of(resolved), List.of()))
        .isInstanceOf(RuntimeException.class);

    assertThat(session.status()).isEqualTo(SessionStatus.FAILED);
    assertThat(session.failureReason()).isEqualTo("DIFF_GENERATION_FAILED");
    ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
    verify(sessionRepository).save(captor.capture());
    assertThat(captor.getValue().status()).isEqualTo(SessionStatus.FAILED);
  }
}
