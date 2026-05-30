package com.bluesteel.adapters.in.web.session;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bluesteel.BlueSteelApplication;
import com.bluesteel.application.model.session.DiffPayload;
import com.bluesteel.application.model.session.SessionStatusView;
import com.bluesteel.application.model.session.SubmitSessionCommand;
import com.bluesteel.application.model.session.SubmitSessionResult;
import com.bluesteel.application.port.in.auth.LoginUseCase;
import com.bluesteel.application.port.in.auth.LogoutUseCase;
import com.bluesteel.application.port.in.auth.RefreshTokenUseCase;
import com.bluesteel.application.port.in.campaign.ChangeMemberRoleUseCase;
import com.bluesteel.application.port.in.campaign.CreateCampaignUseCase;
import com.bluesteel.application.port.in.campaign.GetCampaignUseCase;
import com.bluesteel.application.port.in.campaign.InviteCampaignMemberUseCase;
import com.bluesteel.application.port.in.campaign.ListCampaignsUseCase;
import com.bluesteel.application.port.in.campaign.RemoveMemberUseCase;
import com.bluesteel.application.port.in.health.CheckHealthUseCase;
import com.bluesteel.application.port.in.session.CommitSessionUseCase;
import com.bluesteel.application.port.in.session.DiscardSessionUseCase;
import com.bluesteel.application.port.in.session.GetSessionDiffUseCase;
import com.bluesteel.application.port.in.session.GetSessionStatusUseCase;
import com.bluesteel.application.port.in.session.SubmitSessionUseCase;
import com.bluesteel.application.port.in.user.AdminBootstrapUseCase;
import com.bluesteel.application.port.in.user.ChangePasswordUseCase;
import com.bluesteel.application.port.in.user.GetCurrentUserUseCase;
import com.bluesteel.application.port.in.user.InvitePlatformUserUseCase;
import com.bluesteel.application.port.in.user.SearchUsersUseCase;
import com.bluesteel.domain.exception.ActiveSessionExistsException;
import com.bluesteel.domain.exception.CommitValidationException;
import com.bluesteel.domain.exception.InvalidSessionStateTransitionException;
import com.bluesteel.domain.exception.SessionNotFoundException;
import com.bluesteel.domain.exception.UnauthorizedException;
import com.bluesteel.domain.session.SessionStatus;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(
    classes = BlueSteelApplication.class,
    webEnvironment = WebEnvironment.MOCK,
    properties = {
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
          + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
          + "org.springframework.boot.data.jpa.autoconfigure.JpaRepositoriesAutoConfiguration,"
          + "org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration",
      "admin.email=admin@test.com",
      "admin.password=AdminTest!Password123",
      "jwt.secret=test-jwt-secret-test-jwt-secret-test-jwt-secret!"
    })
@DisplayName("SessionController")
class SessionControllerTest {

  @MockitoBean private AdminBootstrapUseCase adminBootstrapUseCase;
  @MockitoBean private InvitePlatformUserUseCase invitePlatformUserUseCase;
  @MockitoBean private GetCurrentUserUseCase getCurrentUserUseCase;
  @MockitoBean private ChangePasswordUseCase changePasswordUseCase;
  @MockitoBean private CreateCampaignUseCase createCampaignUseCase;
  @MockitoBean private GetCampaignUseCase getCampaignUseCase;
  @MockitoBean private ListCampaignsUseCase listCampaignsUseCase;
  @MockitoBean private InviteCampaignMemberUseCase inviteCampaignMemberUseCase;
  @MockitoBean private ChangeMemberRoleUseCase changeMemberRoleUseCase;
  @MockitoBean private RemoveMemberUseCase removeMemberUseCase;
  @MockitoBean private LoginUseCase loginUseCase;
  @MockitoBean private LogoutUseCase logoutUseCase;
  @MockitoBean private RefreshTokenUseCase refreshTokenUseCase;
  @MockitoBean private CheckHealthUseCase checkHealthUseCase;
  @MockitoBean private SearchUsersUseCase searchUsersUseCase;
  @MockitoBean private SubmitSessionUseCase submitSessionUseCase;
  @MockitoBean private GetSessionStatusUseCase getSessionStatusUseCase;
  @MockitoBean private DiscardSessionUseCase discardSessionUseCase;
  @MockitoBean private GetSessionDiffUseCase getSessionDiffUseCase;
  @MockitoBean private CommitSessionUseCase commitSessionUseCase;

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  private static final UUID CAMPAIGN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID SESSION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID CALLER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @BeforeEach
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  @DisplayName("should return 202 with sessionId and PENDING status on successful submission")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void submit_validRequest_returns202() throws Exception {
    SubmitSessionResult result = new SubmitSessionResult(SESSION_ID, SessionStatus.PENDING);
    when(submitSessionUseCase.submit(
            new SubmitSessionCommand(CALLER_ID, CAMPAIGN_ID, "The heroes entered the dungeon.")))
        .thenReturn(result);

    mockMvc
        .perform(
            post("/api/v1/campaigns/{id}/sessions", CAMPAIGN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "summaryText": "The heroes entered the dungeon." }
                    """))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.data.sessionId").value(SESSION_ID.toString()))
        .andExpect(jsonPath("$.data.status").value("PENDING"));
  }

  @Test
  @DisplayName("should return 400 when summaryText is blank")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void submit_blankSummaryText_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/campaigns/{id}/sessions", CAMPAIGN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "summaryText": "" }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("should return 409 when an active session already exists")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void submit_activeSessionExists_returns409() throws Exception {
    when(submitSessionUseCase.submit(
            new SubmitSessionCommand(CALLER_ID, CAMPAIGN_ID, "The heroes entered the dungeon.")))
        .thenThrow(new ActiveSessionExistsException(SESSION_ID));

    mockMvc
        .perform(
            post("/api/v1/campaigns/{id}/sessions", CAMPAIGN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "summaryText": "The heroes entered the dungeon." }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("ACTIVE_SESSION_EXISTS"));
  }

  @Test
  @DisplayName("should return 401 when request is unauthenticated")
  void submit_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/campaigns/{id}/sessions", CAMPAIGN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "summaryText": "text" }
                    """))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("should return 200 with session status for a valid session")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void getStatus_validSession_returns200() throws Exception {
    SessionStatusView view =
        new SessionStatusView(SESSION_ID, SessionStatus.FAILED, "PIPELINE_NOT_IMPLEMENTED", null);
    when(getSessionStatusUseCase.getStatus(SESSION_ID, CALLER_ID, CAMPAIGN_ID)).thenReturn(view);

    mockMvc
        .perform(get("/api/v1/campaigns/{id}/sessions/{sid}/status", CAMPAIGN_ID, SESSION_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.sessionId").value(SESSION_ID.toString()))
        .andExpect(jsonPath("$.data.status").value("FAILED"))
        .andExpect(jsonPath("$.data.failureReason").value("PIPELINE_NOT_IMPLEMENTED"));
  }

  @Test
  @DisplayName("should return 404 when session does not exist")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void getStatus_sessionNotFound_returns404() throws Exception {
    when(getSessionStatusUseCase.getStatus(SESSION_ID, CALLER_ID, CAMPAIGN_ID))
        .thenThrow(new SessionNotFoundException("Session not found: " + SESSION_ID));

    mockMvc
        .perform(get("/api/v1/campaigns/{id}/sessions/{sid}/status", CAMPAIGN_ID, SESSION_ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errors[0].code").value("SESSION_NOT_FOUND"));
  }

  @Test
  @DisplayName("should return 200 when GM discards a draft session")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void discard_gmWithDraftSession_returns200() throws Exception {
    mockMvc
        .perform(delete("/api/v1/campaigns/{id}/sessions/{sid}", CAMPAIGN_ID, SESSION_ID))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("should return 409 when session is not in DRAFT status on discard")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void discard_sessionNotDraft_returns409() throws Exception {
    doThrow(new InvalidSessionStateTransitionException("discard requires DRAFT but was FAILED"))
        .when(discardSessionUseCase)
        .discard(SESSION_ID, CALLER_ID, CAMPAIGN_ID);

    mockMvc
        .perform(delete("/api/v1/campaigns/{id}/sessions/{sid}", CAMPAIGN_ID, SESSION_ID))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_SESSION_STATE"));
  }

  @Test
  @DisplayName("should return 404 when session to discard does not exist")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void discard_sessionNotFound_returns404() throws Exception {
    doThrow(new SessionNotFoundException("Session not found: " + SESSION_ID))
        .when(discardSessionUseCase)
        .discard(SESSION_ID, CALLER_ID, CAMPAIGN_ID);

    mockMvc
        .perform(delete("/api/v1/campaigns/{id}/sessions/{sid}", CAMPAIGN_ID, SESSION_ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errors[0].code").value("SESSION_NOT_FOUND"));
  }

  @Test
  @DisplayName("should return 200 with DiffPayload when session is in DRAFT status")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void getDiff_draftSession_returns200WithPayload() throws Exception {
    DiffPayload diff =
        new DiffPayload(
            "Heroes stormed the fortress.",
            java.util.List.of(),
            java.util.List.of(),
            java.util.List.of(),
            java.util.List.of(),
            java.util.List.of());
    when(getSessionDiffUseCase.getDiff(CALLER_ID, CAMPAIGN_ID, SESSION_ID)).thenReturn(diff);

    mockMvc
        .perform(get("/api/v1/campaigns/{id}/sessions/{sid}/diff", CAMPAIGN_ID, SESSION_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.narrativeSummaryHeader").value("Heroes stormed the fortress."));
  }

  @Test
  @DisplayName("should return 404 when session diff is not available")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void getDiff_sessionNotFound_returns404() throws Exception {
    when(getSessionDiffUseCase.getDiff(CALLER_ID, CAMPAIGN_ID, SESSION_ID))
        .thenThrow(new SessionNotFoundException("Session not found: " + SESSION_ID));

    mockMvc
        .perform(get("/api/v1/campaigns/{id}/sessions/{sid}/diff", CAMPAIGN_ID, SESSION_ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errors[0].code").value("SESSION_NOT_FOUND"));
  }

  @Test
  @DisplayName("should return 403 when caller is not authorized to view the diff")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void getDiff_unauthorized_returns403() throws Exception {
    when(getSessionDiffUseCase.getDiff(CALLER_ID, CAMPAIGN_ID, SESSION_ID))
        .thenThrow(new UnauthorizedException("Only GMs and Editors may view session diffs"));

    mockMvc
        .perform(get("/api/v1/campaigns/{id}/sessions/{sid}/diff", CAMPAIGN_ID, SESSION_ID))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errors[0].code").value("FORBIDDEN"));
  }

  @Test
  @DisplayName("should return 401 when request is unauthenticated")
  void getDiff_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(get("/api/v1/campaigns/{id}/sessions/{sid}/diff", CAMPAIGN_ID, SESSION_ID))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("should return 200 when commit payload is valid")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void commit_validRequest_returns200() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/campaigns/{id}/sessions/{sid}/commit", CAMPAIGN_ID, SESSION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "cardDecisions": [
                        { "cardId": "33333333-3333-3333-3333-333333333333", "action": "accept" }
                      ],
                      "uncertainResolutions": [],
                      "acknowledgedConflicts": []
                    }
                    """))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("should return 400 when action is edit but editedFields is empty")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void commit_editActionWithoutEditedFields_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/campaigns/{id}/sessions/{sid}/commit", CAMPAIGN_ID, SESSION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "cardDecisions": [
                        { "cardId": "33333333-3333-3333-3333-333333333333", "action": "edit" }
                      ],
                      "uncertainResolutions": [],
                      "acknowledgedConflicts": []
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("should return 400 when resolution is MATCH but matchedEntityId is null")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void commit_matchResolutionWithoutMatchedEntityId_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/campaigns/{id}/sessions/{sid}/commit", CAMPAIGN_ID, SESSION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "cardDecisions": [
                        { "cardId": "33333333-3333-3333-3333-333333333333", "action": "accept" }
                      ],
                      "uncertainResolutions": [
                        { "cardId": "44444444-4444-4444-4444-444444444444", "resolution": "MATCH" }
                      ],
                      "acknowledgedConflicts": []
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("should return 422 with code when CommitValidationException is thrown")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void commit_validationException_returns422WithCode() throws Exception {
    doThrow(
            new CommitValidationException(
                "UNCERTAIN_ENTITIES_PRESENT", "Uncertain entities must be resolved"))
        .when(commitSessionUseCase)
        .commit(org.mockito.ArgumentMatchers.any());

    mockMvc
        .perform(
            post("/api/v1/campaigns/{id}/sessions/{sid}/commit", CAMPAIGN_ID, SESSION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "cardDecisions": [
                        { "cardId": "33333333-3333-3333-3333-333333333333", "action": "accept" }
                      ],
                      "uncertainResolutions": [],
                      "acknowledgedConflicts": []
                    }
                    """))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("UNCERTAIN_ENTITIES_PRESENT"));
  }
}
