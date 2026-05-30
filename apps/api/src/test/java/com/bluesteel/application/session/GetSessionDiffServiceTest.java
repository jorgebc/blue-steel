package com.bluesteel.application.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluesteel.application.model.session.DiffPayload;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.session.SessionRepository;
import com.bluesteel.application.service.session.GetSessionDiffService;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.SessionNotFoundException;
import com.bluesteel.domain.exception.UnauthorizedException;
import com.bluesteel.domain.session.Session;
import com.bluesteel.domain.session.SessionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetSessionDiffService")
class GetSessionDiffServiceTest {

  @Mock private CampaignMembershipPort membershipPort;
  @Mock private SessionRepository sessionRepository;

  private ObjectMapper objectMapper;
  private GetSessionDiffService sut;

  private static final UUID CALLER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID CAMPAIGN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID SESSION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    sut = new GetSessionDiffService(membershipPort, sessionRepository, objectMapper);
  }

  private String minimalDiffPayloadJson() throws Exception {
    DiffPayload payload =
        new DiffPayload("Summary.", List.of(), List.of(), List.of(), List.of(), List.of());
    return objectMapper.writeValueAsString(payload);
  }

  private Session draftSession(String diffPayloadJson) {
    return Session.reconstitute(
        SESSION_ID,
        CAMPAIGN_ID,
        CALLER_ID,
        SessionStatus.DRAFT,
        null,
        null,
        diffPayloadJson,
        null,
        Instant.now(),
        Instant.now());
  }

  private Session processingSession() {
    return Session.reconstitute(
        SESSION_ID,
        CAMPAIGN_ID,
        CALLER_ID,
        SessionStatus.PROCESSING,
        null,
        null,
        null,
        null,
        Instant.now(),
        Instant.now());
  }

  @Test
  @DisplayName("should throw UnauthorizedException when caller is not a campaign member")
  void getDiff_nonMember_throwsUnauthorized() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> sut.getDiff(CALLER_ID, CAMPAIGN_ID, SESSION_ID))
        .isInstanceOf(UnauthorizedException.class);

    verify(sessionRepository, never()).findById(SESSION_ID);
  }

  @Test
  @DisplayName("should throw UnauthorizedException when caller has PLAYER role")
  void getDiff_playerRole_throwsUnauthorized() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));

    assertThatThrownBy(() -> sut.getDiff(CALLER_ID, CAMPAIGN_ID, SESSION_ID))
        .isInstanceOf(UnauthorizedException.class)
        .hasMessageContaining("Only GMs and Editors");

    verify(sessionRepository, never()).findById(SESSION_ID);
  }

  @Test
  @DisplayName("should return DiffPayload for an EDITOR with a DRAFT session")
  void getDiff_editorRoleWithDraftSession_returnsDiffPayload() throws Exception {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.EDITOR));
    when(sessionRepository.findById(SESSION_ID))
        .thenReturn(Optional.of(draftSession(minimalDiffPayloadJson())));

    DiffPayload result = sut.getDiff(CALLER_ID, CAMPAIGN_ID, SESSION_ID);

    assertThat(result).isNotNull();
    assertThat(result.narrativeSummaryHeader()).isEqualTo("Summary.");
    assertThat(result.actors()).isEmpty();
  }

  @Test
  @DisplayName("should return DiffPayload for a GM with a DRAFT session")
  void getDiff_gmRoleWithDraftSession_returnsDiffPayload() throws Exception {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.GM));
    when(sessionRepository.findById(SESSION_ID))
        .thenReturn(Optional.of(draftSession(minimalDiffPayloadJson())));

    DiffPayload result = sut.getDiff(CALLER_ID, CAMPAIGN_ID, SESSION_ID);

    assertThat(result).isNotNull();
    assertThat(result.narrativeSummaryHeader()).isEqualTo("Summary.");
  }

  @Test
  @DisplayName("should throw SessionNotFoundException when session does not exist")
  void getDiff_sessionNotFound_throwsSessionNotFound() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.GM));
    when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> sut.getDiff(CALLER_ID, CAMPAIGN_ID, SESSION_ID))
        .isInstanceOf(SessionNotFoundException.class)
        .hasMessageContaining(SESSION_ID.toString());
  }

  @Test
  @DisplayName("should throw SessionNotFoundException when session is not in DRAFT status")
  void getDiff_sessionNotDraft_throwsSessionNotFound() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.GM));
    when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(processingSession()));

    assertThatThrownBy(() -> sut.getDiff(CALLER_ID, CAMPAIGN_ID, SESSION_ID))
        .isInstanceOf(SessionNotFoundException.class)
        .hasMessageContaining("not in DRAFT");
  }
}
