package com.bluesteel.application.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluesteel.application.event.SessionSubmittedEvent;
import com.bluesteel.application.model.ingestion.ConflictWarning;
import com.bluesteel.application.model.ingestion.ExtractionResult;
import com.bluesteel.application.model.ingestion.ResolutionOutcome;
import com.bluesteel.application.model.ingestion.ResolvedEntity;
import com.bluesteel.application.port.out.session.NarrativeBlockRepository;
import com.bluesteel.application.port.out.session.SessionRepository;
import com.bluesteel.application.service.session.ConflictDetectionService;
import com.bluesteel.application.service.session.EntityResolutionService;
import com.bluesteel.application.service.session.ExtractionPipelineService;
import com.bluesteel.application.service.session.SessionIngestionEventListener;
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

  @Mock private SessionRepository sessionRepository;
  @Mock private NarrativeBlockRepository narrativeBlockRepository;
  @Mock private ExtractionPipelineService extractionPipelineService;
  @Mock private EntityResolutionService entityResolutionService;
  @Mock private ConflictDetectionService conflictDetectionService;

  private SessionIngestionEventListener sut;

  @BeforeEach
  void setUp() {
    sut =
        new SessionIngestionEventListener(
            sessionRepository,
            narrativeBlockRepository,
            extractionPipelineService,
            entityResolutionService,
            conflictDetectionService);
  }

  @Test
  @DisplayName(
      "should load the session and its narrative block then delegate to ExtractionPipelineService")
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
    when(extractionPipelineService.run(session, rawText)).thenReturn(extractionResult);
    when(entityResolutionService.run(campaignId, extractionResult)).thenReturn(List.of());
    when(conflictDetectionService.run(campaignId, extractionResult, List.of()))
        .thenReturn(List.of());

    sut.onSessionSubmitted(new SessionSubmittedEvent(sessionId, campaignId));

    ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
    ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
    verify(extractionPipelineService).run(sessionCaptor.capture(), textCaptor.capture());

    assertThat(sessionCaptor.getValue()).isSameAs(session);
    assertThat(textCaptor.getValue()).isEqualTo(rawText);
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
    when(extractionPipelineService.run(session, rawText)).thenReturn(extractionResult);
    when(entityResolutionService.run(campaignId, extractionResult)).thenReturn(List.of());
    when(conflictDetectionService.run(campaignId, extractionResult, List.of()))
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
      "should invoke ConflictDetectionService with campaignId, extraction output, and resolved entities after resolution")
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
    when(extractionPipelineService.run(session, rawText)).thenReturn(extractionResult);
    when(entityResolutionService.run(campaignId, extractionResult)).thenReturn(List.of(resolved));
    when(conflictDetectionService.run(campaignId, extractionResult, List.of(resolved)))
        .thenReturn(List.of(new ConflictWarning("Mira", "Contradiction found.")));

    sut.onSessionSubmitted(new SessionSubmittedEvent(sessionId, campaignId));

    ArgumentCaptor<UUID> campaignIdCaptor = ArgumentCaptor.forClass(UUID.class);
    ArgumentCaptor<ExtractionResult> extractionCaptor =
        ArgumentCaptor.forClass(ExtractionResult.class);
    ArgumentCaptor<List<ResolvedEntity>> resolvedCaptor =
        ArgumentCaptor.forClass((Class<List<ResolvedEntity>>) (Class<?>) List.class);
    verify(conflictDetectionService)
        .run(campaignIdCaptor.capture(), extractionCaptor.capture(), resolvedCaptor.capture());

    assertThat(campaignIdCaptor.getValue()).isEqualTo(campaignId);
    assertThat(extractionCaptor.getValue()).isSameAs(extractionResult);
    assertThat(resolvedCaptor.getValue()).containsExactly(resolved);
  }
}
