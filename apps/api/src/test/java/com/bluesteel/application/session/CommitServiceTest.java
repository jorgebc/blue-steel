package com.bluesteel.application.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluesteel.application.event.SessionCommittedEvent;
import com.bluesteel.application.model.commit.CardAction;
import com.bluesteel.application.model.commit.CardDecision;
import com.bluesteel.application.model.commit.CommitPayload;
import com.bluesteel.application.model.session.CommitSessionCommand;
import com.bluesteel.application.model.session.DiffPayload;
import com.bluesteel.application.model.session.ExistingEntityCard;
import com.bluesteel.application.model.session.NewEntityCard;
import com.bluesteel.application.model.worldstate.CommittedEntityVersion;
import com.bluesteel.application.model.worldstate.EntityWriteCommand;
import com.bluesteel.application.model.worldstate.ResolvedEndpoint;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.session.SessionRepository;
import com.bluesteel.application.port.out.worldstate.WorldStatePort;
import com.bluesteel.application.service.session.CommitPayloadValidator;
import com.bluesteel.application.service.session.CommitService;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.InvalidSessionStateTransitionException;
import com.bluesteel.domain.exception.SessionNotFoundException;
import com.bluesteel.domain.exception.UnauthorizedException;
import com.bluesteel.domain.session.Session;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

@DisplayName("CommitService")
class CommitServiceTest {

  private CampaignMembershipPort membershipPort;
  private SessionRepository sessionRepository;
  private WorldStatePort worldStatePort;
  private CommitPayloadValidator validator;
  private ApplicationEventPublisher eventPublisher;
  private CommitService service;

  private final UUID callerId = UUID.randomUUID();
  private final UUID campaignId = UUID.randomUUID();
  private final UUID sessionId = UUID.randomUUID();
  private final UUID existingCardId = UUID.randomUUID();
  private final UUID newCardId = UUID.randomUUID();
  private final UUID existingEntityId = UUID.randomUUID();

  private Session draftSession;
  private DiffPayload diff;
  private CommitPayload payload;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() throws Exception {
    membershipPort = mock(CampaignMembershipPort.class);
    sessionRepository = mock(SessionRepository.class);
    worldStatePort = mock(WorldStatePort.class);
    validator = mock(CommitPayloadValidator.class);
    eventPublisher = mock(ApplicationEventPublisher.class);
    objectMapper = new ObjectMapper();

    service =
        new CommitService(
            membershipPort,
            sessionRepository,
            worldStatePort,
            validator,
            objectMapper,
            eventPublisher);

    diff =
        new DiffPayload(
            "header",
            List.of(
                new ExistingEntityCard(
                    existingCardId, existingEntityId, "actor", "Frodo", Map.of())),
            List.of(new NewEntityCard(newCardId, "space", "Shire", Map.of("name", "Shire"))),
            List.of(),
            List.of(),
            List.of());

    payload =
        new CommitPayload(
            List.of(
                new CardDecision(existingCardId, CardAction.ACCEPT, null),
                new CardDecision(newCardId, CardAction.ACCEPT, null)),
            List.of(),
            List.of());

    String diffJson = objectMapper.writeValueAsString(diff);
    draftSession =
        Session.reconstitute(
            sessionId,
            campaignId,
            callerId,
            com.bluesteel.domain.session.SessionStatus.DRAFT,
            null,
            null,
            diffJson,
            null,
            Instant.now(),
            Instant.now());

    when(membershipPort.resolveRole(campaignId, callerId)).thenReturn(Optional.of(CampaignRole.GM));
    when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(draftSession));
    when(sessionRepository.nextSequenceNumber(campaignId)).thenReturn(1);

    CommittedEntityVersion stubVersion =
        new CommittedEntityVersion(
            "actor", UUID.randomUUID(), UUID.randomUUID(), 1, "Frodo\n{}", "abc");
    when(worldStatePort.writeEntity(org.mockito.ArgumentMatchers.any(EntityWriteCommand.class)))
        .thenReturn(stubVersion);
  }

  @Test
  @DisplayName("should call validator before any WorldStatePort write (D-081)")
  void commit_validatorCalledBeforeWorldStateWrite() {
    InOrder order = inOrder(validator, worldStatePort);

    service.commit(new CommitSessionCommand(callerId, campaignId, sessionId, payload));

    order
        .verify(validator)
        .validate(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
    order
        .verify(worldStatePort, org.mockito.Mockito.atLeastOnce())
        .writeEntity(org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName(
      "should write entity versions, assign sequence number, and publish event on happy path")
  void commit_happyPath_writesVersionsAndPublishesEvent() {
    service.commit(new CommitSessionCommand(callerId, campaignId, sessionId, payload));

    ArgumentCaptor<SessionCommittedEvent> eventCaptor = forClass(SessionCommittedEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());

    SessionCommittedEvent event = eventCaptor.getValue();
    assertThat(event.sessionId()).isEqualTo(sessionId);
    assertThat(event.campaignId()).isEqualTo(campaignId);
    assertThat(event.committedVersions()).hasSize(2);

    assertThat(draftSession.status())
        .isEqualTo(com.bluesteel.domain.session.SessionStatus.COMMITTED);
    assertThat(draftSession.sequenceNumber()).isEqualTo(1);
    assertThat(draftSession.diffPayload()).isNull();

    verify(sessionRepository).save(draftSession);
  }

  @Test
  @DisplayName("should throw UnauthorizedException when caller is not a campaign member")
  void commit_notMember_throwsUnauthorized() {
    when(membershipPort.resolveRole(campaignId, callerId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.commit(new CommitSessionCommand(callerId, campaignId, sessionId, payload)))
        .isInstanceOf(UnauthorizedException.class);

    verify(worldStatePort, never()).writeEntity(org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("should throw UnauthorizedException when caller has PLAYER role")
  void commit_playerRole_throwsUnauthorized() {
    when(membershipPort.resolveRole(campaignId, callerId))
        .thenReturn(Optional.of(CampaignRole.PLAYER));

    assertThatThrownBy(
            () ->
                service.commit(new CommitSessionCommand(callerId, campaignId, sessionId, payload)))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  @DisplayName("should throw SessionNotFoundException when session does not exist")
  void commit_sessionNotFound_throwsNotFound() {
    when(sessionRepository.findById(sessionId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.commit(new CommitSessionCommand(callerId, campaignId, sessionId, payload)))
        .isInstanceOf(SessionNotFoundException.class);
  }

  @Test
  @DisplayName("should throw InvalidSessionStateTransitionException when session is not DRAFT")
  void commit_nonDraftSession_throwsInvalidTransition() {
    Session processingSession =
        Session.reconstitute(
            sessionId,
            campaignId,
            callerId,
            com.bluesteel.domain.session.SessionStatus.PROCESSING,
            null,
            null,
            null,
            null,
            Instant.now(),
            Instant.now());
    when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(processingSession));

    assertThatThrownBy(
            () ->
                service.commit(new CommitSessionCommand(callerId, campaignId, sessionId, payload)))
        .isInstanceOf(InvalidSessionStateTransitionException.class);

    verify(worldStatePort, never()).writeEntity(org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("should resolve relation source/target mentions to entity ids on the write command")
  void commit_relationCard_resolvesEndpoints() throws Exception {
    UUID relationCardId = UUID.randomUUID();
    UUID sourceId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();
    CommittedEntityVersion relationVersion =
        new CommittedEntityVersion(
            "relation", UUID.randomUUID(), UUID.randomUUID(), 1, "rel\n{}", "hash");
    when(worldStatePort.writeEntity(org.mockito.ArgumentMatchers.any(EntityWriteCommand.class)))
        .thenReturn(relationVersion);
    when(worldStatePort.findEndpointByName(campaignId, "Mira"))
        .thenReturn(Optional.of(new ResolvedEndpoint(sourceId, "actor")));
    when(worldStatePort.findEndpointByName(campaignId, "Thornwick"))
        .thenReturn(Optional.of(new ResolvedEndpoint(targetId, "space")));

    CommitService relationService = commitServiceFor(relationDiffWithEndpoints(relationCardId));

    relationService.commit(
        new CommitSessionCommand(
            callerId,
            campaignId,
            sessionId,
            new CommitPayload(
                List.of(new CardDecision(relationCardId, CardAction.ACCEPT, null)),
                List.of(),
                List.of())));

    ArgumentCaptor<EntityWriteCommand> captor = forClass(EntityWriteCommand.class);
    verify(worldStatePort).writeEntity(captor.capture());
    EntityWriteCommand cmd = captor.getValue();
    assertThat(cmd.sourceEntityId()).isEqualTo(sourceId);
    assertThat(cmd.sourceEntityType()).isEqualTo("actor");
    assertThat(cmd.targetEntityId()).isEqualTo(targetId);
    assertThat(cmd.targetEntityType()).isEqualTo("space");
  }

  @Test
  @DisplayName("should leave relation endpoints null when a mention does not name-match any entity")
  void commit_relationCard_unresolvedEndpointsStayNull() throws Exception {
    UUID relationCardId = UUID.randomUUID();
    CommittedEntityVersion relationVersion =
        new CommittedEntityVersion(
            "relation", UUID.randomUUID(), UUID.randomUUID(), 1, "rel\n{}", "hash");
    when(worldStatePort.writeEntity(org.mockito.ArgumentMatchers.any(EntityWriteCommand.class)))
        .thenReturn(relationVersion);
    when(worldStatePort.findEndpointByName(campaignId, "Mira")).thenReturn(Optional.empty());
    when(worldStatePort.findEndpointByName(campaignId, "Thornwick")).thenReturn(Optional.empty());

    CommitService relationService = commitServiceFor(relationDiffWithEndpoints(relationCardId));

    relationService.commit(
        new CommitSessionCommand(
            callerId,
            campaignId,
            sessionId,
            new CommitPayload(
                List.of(new CardDecision(relationCardId, CardAction.ACCEPT, null)),
                List.of(),
                List.of())));

    ArgumentCaptor<EntityWriteCommand> captor = forClass(EntityWriteCommand.class);
    verify(worldStatePort).writeEntity(captor.capture());
    EntityWriteCommand cmd = captor.getValue();
    assertThat(cmd.sourceEntityId()).isNull();
    assertThat(cmd.sourceEntityType()).isNull();
    assertThat(cmd.targetEntityId()).isNull();
    assertThat(cmd.targetEntityType()).isNull();
  }

  @Test
  @DisplayName("should write relation cards after non-relation cards regardless of decision order")
  void commit_writesRelationsLastRegardlessOfOrder() throws Exception {
    UUID actorCardId = UUID.randomUUID();
    UUID relationCardId = UUID.randomUUID();
    DiffPayload diffPayload =
        new DiffPayload(
            "header",
            List.of(new NewEntityCard(actorCardId, "actor", "Mira", Map.of("name", "Mira"))),
            List.of(),
            List.of(),
            List.of(
                new NewEntityCard(
                    relationCardId,
                    "relation",
                    "Mira guides the party",
                    Map.of("name", "Mira guides the party", "sourceMention", "Mira"))),
            List.of());
    CommitService relationService = commitServiceFor(diffPayload);
    when(worldStatePort.findEndpointByName(campaignId, "Mira")).thenReturn(Optional.empty());

    // The relation decision is listed BEFORE the actor decision — the service must still write the
    // actor first so the relation's endpoint name-match can see it.
    relationService.commit(
        new CommitSessionCommand(
            callerId,
            campaignId,
            sessionId,
            new CommitPayload(
                List.of(
                    new CardDecision(relationCardId, CardAction.ACCEPT, null),
                    new CardDecision(actorCardId, CardAction.ACCEPT, null)),
                List.of(),
                List.of())));

    ArgumentCaptor<EntityWriteCommand> captor = forClass(EntityWriteCommand.class);
    verify(worldStatePort, org.mockito.Mockito.times(2)).writeEntity(captor.capture());
    assertThat(captor.getAllValues().get(0).entityType()).isEqualTo("actor");
    assertThat(captor.getAllValues().get(1).entityType()).isEqualTo("relation");
  }

  @Test
  @DisplayName(
      "should resolve event space/actor mentions to entity ids on the write command (F4.6.4)")
  void commit_eventCard_resolvesLinks() throws Exception {
    UUID eventCardId = UUID.randomUUID();
    UUID spaceId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    CommittedEntityVersion eventVersion =
        new CommittedEntityVersion(
            "event", UUID.randomUUID(), UUID.randomUUID(), 1, "ev\n{}", "hash");
    when(worldStatePort.writeEntity(org.mockito.ArgumentMatchers.any(EntityWriteCommand.class)))
        .thenReturn(eventVersion);
    when(worldStatePort.findEntityIdByName(campaignId, "Thornwick", "space"))
        .thenReturn(Optional.of(spaceId));
    when(worldStatePort.findEntityIdByName(campaignId, "Mira", "actor"))
        .thenReturn(Optional.of(actorId));

    DiffPayload eventDiff =
        new DiffPayload(
            "header",
            List.of(),
            List.of(),
            List.of(
                new NewEntityCard(
                    eventCardId,
                    "event",
                    "The Arrival",
                    Map.of(
                        "name",
                        "The Arrival",
                        "spaceMention",
                        "Thornwick",
                        "involvedActorMentions",
                        List.of("Mira"),
                        "eventType",
                        "arrival"))),
            List.of(),
            List.of());
    CommitService eventService = commitServiceFor(eventDiff);

    eventService.commit(
        new CommitSessionCommand(
            callerId,
            campaignId,
            sessionId,
            new CommitPayload(
                List.of(new CardDecision(eventCardId, CardAction.ACCEPT, null)),
                List.of(),
                List.of())));

    ArgumentCaptor<EntityWriteCommand> captor = forClass(EntityWriteCommand.class);
    verify(worldStatePort).writeEntity(captor.capture());
    EntityWriteCommand cmd = captor.getValue();
    assertThat(cmd.spaceId()).isEqualTo(spaceId);
    assertThat(cmd.involvedActorIds()).containsExactly(actorId);
    assertThat(cmd.eventType()).isEqualTo("arrival");
  }

  @Test
  @DisplayName(
      "should write event cards after actor/space cards regardless of decision order (F4.6.4)")
  void commit_writesEventsAfterActorsRegardlessOfOrder() throws Exception {
    UUID actorCardId = UUID.randomUUID();
    UUID eventCardId = UUID.randomUUID();
    DiffPayload diffPayload =
        new DiffPayload(
            "header",
            List.of(new NewEntityCard(actorCardId, "actor", "Mira", Map.of("name", "Mira"))),
            List.of(),
            List.of(
                new NewEntityCard(
                    eventCardId,
                    "event",
                    "The Arrival",
                    Map.of("name", "The Arrival", "involvedActorMentions", List.of("Mira")))),
            List.of(),
            List.of());
    CommitService eventService = commitServiceFor(diffPayload);
    when(worldStatePort.findEntityIdByName(campaignId, "Mira", "actor"))
        .thenReturn(Optional.empty());

    // The event decision is listed BEFORE the actor decision — the service must still write the
    // actor first so the event's involved-actor name-match can see it.
    eventService.commit(
        new CommitSessionCommand(
            callerId,
            campaignId,
            sessionId,
            new CommitPayload(
                List.of(
                    new CardDecision(eventCardId, CardAction.ACCEPT, null),
                    new CardDecision(actorCardId, CardAction.ACCEPT, null)),
                List.of(),
                List.of())));

    ArgumentCaptor<EntityWriteCommand> captor = forClass(EntityWriteCommand.class);
    verify(worldStatePort, org.mockito.Mockito.times(2)).writeEntity(captor.capture());
    assertThat(captor.getAllValues().get(0).entityType()).isEqualTo("actor");
    assertThat(captor.getAllValues().get(1).entityType()).isEqualTo("event");
  }

  private DiffPayload relationDiffWithEndpoints(UUID relationCardId) {
    return new DiffPayload(
        "header",
        List.of(),
        List.of(),
        List.of(),
        List.of(
            new NewEntityCard(
                relationCardId,
                "relation",
                "Mira guides the party",
                Map.of(
                    "name",
                    "Mira guides the party",
                    "sourceMention",
                    "Mira",
                    "targetMention",
                    "Thornwick"))),
        List.of());
  }

  private CommitService commitServiceFor(DiffPayload relationDiff) throws Exception {
    String diffJson = objectMapper.writeValueAsString(relationDiff);
    Session relationSession =
        Session.reconstitute(
            sessionId,
            campaignId,
            callerId,
            com.bluesteel.domain.session.SessionStatus.DRAFT,
            null,
            null,
            diffJson,
            null,
            Instant.now(),
            Instant.now());
    when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(relationSession));
    return service;
  }
}
