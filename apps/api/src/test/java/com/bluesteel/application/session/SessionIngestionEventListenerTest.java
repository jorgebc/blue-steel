package com.bluesteel.application.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluesteel.application.event.SessionSubmittedEvent;
import com.bluesteel.application.model.ingestion.ConflictWarning;
import com.bluesteel.application.model.ingestion.ExtractionResult;
import com.bluesteel.application.model.ingestion.ResolutionOutcome;
import com.bluesteel.application.model.ingestion.ResolvedEntity;
import com.bluesteel.application.port.out.campaign.CampaignRepository;
import com.bluesteel.application.port.out.session.NarrativeBlockRepository;
import com.bluesteel.application.port.out.session.SessionRepository;
import com.bluesteel.application.service.session.ConflictDetectionService;
import com.bluesteel.application.service.session.DiffGenerationService;
import com.bluesteel.application.service.session.EntityResolutionService;
import com.bluesteel.application.service.session.ExtractionPipelineService;
import com.bluesteel.application.service.session.SessionIngestionEventListener;
import com.bluesteel.domain.campaign.Campaign;
import com.bluesteel.domain.session.NarrativeBlock;
import com.bluesteel.domain.session.Session;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionIngestionEventListener")
class SessionIngestionEventListenerTest {

  private static final String CONTENT_LANGUAGE = "es";

  @Mock private SessionRepository sessionRepository;
  @Mock private NarrativeBlockRepository narrativeBlockRepository;
  @Mock private CampaignRepository campaignRepository;
  @Mock private ExtractionPipelineService extractionPipelineService;
  @Mock private EntityResolutionService entityResolutionService;
  @Mock private ConflictDetectionService conflictDetectionService;
  @Mock private DiffGenerationService diffGenerationService;

  private SessionIngestionEventListener sut;

  @BeforeEach
  void setUp() {
    sut =
        new SessionIngestionEventListener(
            sessionRepository,
            narrativeBlockRepository,
            campaignRepository,
            extractionPipelineService,
            entityResolutionService,
            conflictDetectionService,
            diffGenerationService,
            Runnable::run);
  }

  private Campaign campaign(UUID campaignId, UUID ownerId) {
    return Campaign.create(campaignId, "Test Campaign", ownerId, Instant.now(), CONTENT_LANGUAGE);
  }

  @Test
  @DisplayName(
      "should load the session and its narrative block then delegate to ExtractionPipelineService with the campaign language")
  void onSessionSubmitted_delegatesToExtractionPipelineService() {
    UUID sessionId = UUID.randomUUID();
    UUID campaignId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    String rawText = "The heroes stormed the fortress at dawn.";

    Session session = Session.create(sessionId, campaignId, ownerId, Instant.now());
    NarrativeBlock block =
        NarrativeBlock.create(UUID.randomUUID(), sessionId, rawText, 10, Instant.now());
    ExtractionResult extractionResult =
        new ExtractionResult("Summary.", List.of(), List.of(), List.of(), List.of());

    when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
    when(narrativeBlockRepository.findBySessionId(sessionId)).thenReturn(Optional.of(block));
    when(campaignRepository.findById(campaignId))
        .thenReturn(Optional.of(campaign(campaignId, ownerId)));
    when(extractionPipelineService.run(session, rawText, CONTENT_LANGUAGE))
        .thenReturn(extractionResult);
    when(entityResolutionService.run(campaignId, extractionResult)).thenReturn(List.of());
    when(conflictDetectionService.run(campaignId, extractionResult, List.of(), CONTENT_LANGUAGE))
        .thenReturn(List.of());

    sut.onSessionSubmitted(new SessionSubmittedEvent(sessionId, campaignId));

    ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
    ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> languageCaptor = ArgumentCaptor.forClass(String.class);
    verify(extractionPipelineService)
        .run(sessionCaptor.capture(), textCaptor.capture(), languageCaptor.capture());

    assertThat(sessionCaptor.getValue()).isSameAs(session);
    assertThat(textCaptor.getValue()).isEqualTo(rawText);
    assertThat(languageCaptor.getValue()).isEqualTo(CONTENT_LANGUAGE);
  }

  @Test
  @DisplayName(
      "should invoke EntityResolutionService with campaignId and extraction output after extraction")
  void onSessionSubmitted_delegatesToEntityResolutionServiceAfterExtraction() {
    UUID sessionId = UUID.randomUUID();
    UUID campaignId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    String rawText = "The heroes discovered an ancient temple.";

    Session session = Session.create(sessionId, campaignId, ownerId, Instant.now());
    NarrativeBlock block =
        NarrativeBlock.create(UUID.randomUUID(), sessionId, rawText, 10, Instant.now());
    ExtractionResult extractionResult =
        new ExtractionResult("Temple discovered.", List.of(), List.of(), List.of(), List.of());

    when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
    when(narrativeBlockRepository.findBySessionId(sessionId)).thenReturn(Optional.of(block));
    when(campaignRepository.findById(campaignId))
        .thenReturn(Optional.of(campaign(campaignId, ownerId)));
    when(extractionPipelineService.run(session, rawText, CONTENT_LANGUAGE))
        .thenReturn(extractionResult);
    when(entityResolutionService.run(campaignId, extractionResult)).thenReturn(List.of());
    when(conflictDetectionService.run(campaignId, extractionResult, List.of(), CONTENT_LANGUAGE))
        .thenReturn(List.of());

    sut.onSessionSubmitted(new SessionSubmittedEvent(sessionId, campaignId));

    ArgumentCaptor<UUID> campaignIdCaptor = ArgumentCaptor.forClass(UUID.class);
    ArgumentCaptor<ExtractionResult> extractionCaptor =
        ArgumentCaptor.forClass(ExtractionResult.class);
    verify(entityResolutionService).run(campaignIdCaptor.capture(), extractionCaptor.capture());

    assertThat(campaignIdCaptor.getValue()).isEqualTo(campaignId);
    assertThat(extractionCaptor.getValue()).isSameAs(extractionResult);
  }

  @Test
  @DisplayName(
      "should invoke ConflictDetectionService with campaignId, extraction output, resolved entities, and campaign language after resolution")
  void onSessionSubmitted_delegatesToConflictDetectionServiceAfterResolution() {
    UUID sessionId = UUID.randomUUID();
    UUID campaignId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    String rawText = "Mira appeared at the castle gates.";

    Session session = Session.create(sessionId, campaignId, ownerId, Instant.now());
    NarrativeBlock block =
        NarrativeBlock.create(UUID.randomUUID(), sessionId, rawText, 10, Instant.now());
    ExtractionResult extractionResult =
        new ExtractionResult("Mira appeared.", List.of(), List.of(), List.of(), List.of());
    ResolvedEntity resolved = new ResolvedEntity(null, ResolutionOutcome.MATCH, UUID.randomUUID());

    when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
    when(narrativeBlockRepository.findBySessionId(sessionId)).thenReturn(Optional.of(block));
    when(campaignRepository.findById(campaignId))
        .thenReturn(Optional.of(campaign(campaignId, ownerId)));
    when(extractionPipelineService.run(session, rawText, CONTENT_LANGUAGE))
        .thenReturn(extractionResult);
    when(entityResolutionService.run(campaignId, extractionResult)).thenReturn(List.of(resolved));
    when(conflictDetectionService.run(
            campaignId, extractionResult, List.of(resolved), CONTENT_LANGUAGE))
        .thenReturn(List.of(new ConflictWarning("Mira", "Contradiction found.")));

    sut.onSessionSubmitted(new SessionSubmittedEvent(sessionId, campaignId));

    ArgumentCaptor<UUID> campaignIdCaptor = ArgumentCaptor.forClass(UUID.class);
    ArgumentCaptor<ExtractionResult> extractionCaptor =
        ArgumentCaptor.forClass(ExtractionResult.class);
    ArgumentCaptor<List<ResolvedEntity>> resolvedCaptor =
        ArgumentCaptor.forClass((Class<List<ResolvedEntity>>) (Class<?>) List.class);
    ArgumentCaptor<String> languageCaptor = ArgumentCaptor.forClass(String.class);
    verify(conflictDetectionService)
        .run(
            campaignIdCaptor.capture(),
            extractionCaptor.capture(),
            resolvedCaptor.capture(),
            languageCaptor.capture());

    assertThat(campaignIdCaptor.getValue()).isEqualTo(campaignId);
    assertThat(extractionCaptor.getValue()).isSameAs(extractionResult);
    assertThat(resolvedCaptor.getValue()).containsExactly(resolved);
    assertThat(languageCaptor.getValue()).isEqualTo(CONTENT_LANGUAGE);
  }

  @Test
  @DisplayName(
      "should invoke DiffGenerationService with session, extraction output, resolved entities, and conflicts after conflict detection")
  void onSessionSubmitted_delegatesToDiffGenerationServiceAfterConflictDetection() {
    UUID sessionId = UUID.randomUUID();
    UUID campaignId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    String rawText = "The dragon descended upon the village.";

    Session session = Session.create(sessionId, campaignId, ownerId, Instant.now());
    NarrativeBlock block =
        NarrativeBlock.create(UUID.randomUUID(), sessionId, rawText, 10, Instant.now());
    ExtractionResult extractionResult =
        new ExtractionResult("Dragon attack.", List.of(), List.of(), List.of(), List.of());
    ResolvedEntity resolved = new ResolvedEntity(null, ResolutionOutcome.NEW, null);
    ConflictWarning conflict = new ConflictWarning("Dragon", "Unknown origin.");

    when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
    when(narrativeBlockRepository.findBySessionId(sessionId)).thenReturn(Optional.of(block));
    when(campaignRepository.findById(campaignId))
        .thenReturn(Optional.of(campaign(campaignId, ownerId)));
    when(extractionPipelineService.run(session, rawText, CONTENT_LANGUAGE))
        .thenReturn(extractionResult);
    when(entityResolutionService.run(campaignId, extractionResult)).thenReturn(List.of(resolved));
    when(conflictDetectionService.run(
            campaignId, extractionResult, List.of(resolved), CONTENT_LANGUAGE))
        .thenReturn(List.of(conflict));

    sut.onSessionSubmitted(new SessionSubmittedEvent(sessionId, campaignId));

    ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
    ArgumentCaptor<ExtractionResult> extractionCaptor =
        ArgumentCaptor.forClass(ExtractionResult.class);
    ArgumentCaptor<List<ResolvedEntity>> resolvedCaptor =
        ArgumentCaptor.forClass((Class<List<ResolvedEntity>>) (Class<?>) List.class);
    ArgumentCaptor<List<ConflictWarning>> conflictsCaptor =
        ArgumentCaptor.forClass((Class<List<ConflictWarning>>) (Class<?>) List.class);
    verify(diffGenerationService)
        .run(
            sessionCaptor.capture(),
            extractionCaptor.capture(),
            resolvedCaptor.capture(),
            conflictsCaptor.capture());

    assertThat(sessionCaptor.getValue()).isSameAs(session);
    assertThat(extractionCaptor.getValue()).isSameAs(extractionResult);
    assertThat(resolvedCaptor.getValue()).containsExactly(resolved);
    assertThat(conflictsCaptor.getValue()).containsExactly(conflict);
  }
}
