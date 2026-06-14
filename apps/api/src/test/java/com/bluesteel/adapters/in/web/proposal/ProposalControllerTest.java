package com.bluesteel.adapters.in.web.proposal;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bluesteel.BlueSteelApplication;
import com.bluesteel.application.model.proposal.CoSignProposalCommand;
import com.bluesteel.application.model.proposal.CreateProposalCommand;
import com.bluesteel.application.model.proposal.ProposalListView;
import com.bluesteel.application.model.proposal.ProposalView;
import com.bluesteel.application.port.in.campaign.InviteCampaignMemberUseCase;
import com.bluesteel.application.port.in.health.CheckHealthUseCase;
import com.bluesteel.application.port.in.proposal.CoSignProposalUseCase;
import com.bluesteel.application.port.in.proposal.CreateProposalUseCase;
import com.bluesteel.application.port.in.proposal.ListProposalsUseCase;
import com.bluesteel.application.port.in.user.AdminBootstrapUseCase;
import com.bluesteel.application.port.in.user.ChangePasswordUseCase;
import com.bluesteel.application.port.in.user.GetCurrentUserUseCase;
import com.bluesteel.application.port.in.user.InvitePlatformUserUseCase;
import com.bluesteel.domain.exception.AuthorCannotCoSignException;
import com.bluesteel.domain.exception.ConcurrentProposalException;
import com.bluesteel.domain.exception.DuplicateVoteException;
import com.bluesteel.domain.exception.EmptyDeltaException;
import com.bluesteel.domain.exception.ProposalNotFoundException;
import com.bluesteel.domain.exception.ProposalTargetNotFoundException;
import com.bluesteel.domain.exception.UnsupportedTargetTypeException;
import com.bluesteel.domain.proposal.ProposalStatus;
import com.bluesteel.domain.proposal.ProposalTargetType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
@DisplayName("ProposalController")
class ProposalControllerTest {

  @MockitoBean private CreateProposalUseCase createProposalUseCase;
  @MockitoBean private ListProposalsUseCase listProposalsUseCase;
  @MockitoBean private CoSignProposalUseCase coSignProposalUseCase;

  @MockitoBean private CheckHealthUseCase checkHealthUseCase;
  @MockitoBean private AdminBootstrapUseCase adminBootstrapUseCase;
  @MockitoBean private InvitePlatformUserUseCase invitePlatformUserUseCase;
  @MockitoBean private InviteCampaignMemberUseCase inviteCampaignMemberUseCase;
  @MockitoBean private GetCurrentUserUseCase getCurrentUserUseCase;
  @MockitoBean private ChangePasswordUseCase changePasswordUseCase;

  @Autowired private WebApplicationContext context;
  private MockMvc mockMvc;

  private static final UUID CAMPAIGN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID TARGET_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID SESSION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID PROPOSAL_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
  private static final String CALLER = "55555555-5555-5555-5555-555555555555";
  private static final UUID CALLER_ID = UUID.fromString(CALLER);
  private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");

  @BeforeEach
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  private static final String CREATE_BODY =
      """
      {
        "targetType": "actor",
        "targetId": "22222222-2222-2222-2222-222222222222",
        "sessionId": "33333333-3333-3333-3333-333333333333",
        "proposedDelta": { "name": "New Name" }
      }
      """;

  // -------------------------------------------------------------------------
  // POST /api/v1/campaigns/{id}/proposals
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("should return 201 with the created proposal on success")
  @WithMockUser(username = CALLER, roles = "USER")
  void create_valid_returns201() throws Exception {
    when(createProposalUseCase.create(
            new CreateProposalCommand(
                CALLER_ID,
                CAMPAIGN_ID,
                "actor",
                TARGET_ID,
                SESSION_ID,
                Map.of("name", "New Name"))))
        .thenReturn(view(ProposalStatus.OPEN));

    mockMvc
        .perform(
            post("/api/v1/campaigns/{id}/proposals", CAMPAIGN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.proposalId").value(PROPOSAL_ID.toString()))
        .andExpect(jsonPath("$.data.targetType").value("ACTOR"))
        .andExpect(jsonPath("$.data.status").value("OPEN"))
        .andExpect(jsonPath("$.data.proposedDelta.name").value("New Name"))
        .andExpect(jsonPath("$.data.sessionId").value(SESSION_ID.toString()));
  }

  @Test
  @DisplayName("should return 400 when targetType is blank")
  @WithMockUser(username = CALLER, roles = "USER")
  void create_blankTargetType_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/campaigns/{id}/proposals", CAMPAIGN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "targetType": "",
                      "targetId": "22222222-2222-2222-2222-222222222222",
                      "sessionId": "33333333-3333-3333-3333-333333333333",
                      "proposedDelta": { "name": "X" }
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VALIDATION_ERROR"));
  }

  @Test
  @DisplayName("should return 400 when proposedDelta is missing")
  @WithMockUser(username = CALLER, roles = "USER")
  void create_missingDelta_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/campaigns/{id}/proposals", CAMPAIGN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "targetType": "actor",
                      "targetId": "22222222-2222-2222-2222-222222222222",
                      "sessionId": "33333333-3333-3333-3333-333333333333"
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("should return 422 UNSUPPORTED_TARGET_TYPE when target type is out of scope")
  @WithMockUser(username = CALLER, roles = "USER")
  void create_unsupportedType_returns422() throws Exception {
    when(createProposalUseCase.create(
            new CreateProposalCommand(
                CALLER_ID,
                CAMPAIGN_ID,
                "actor",
                TARGET_ID,
                SESSION_ID,
                Map.of("name", "New Name"))))
        .thenThrow(new UnsupportedTargetTypeException("nope"));

    mockMvc
        .perform(
            post("/api/v1/campaigns/{id}/proposals", CAMPAIGN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_BODY))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("UNSUPPORTED_TARGET_TYPE"));
  }

  @Test
  @DisplayName("should return 422 EMPTY_DELTA when the delta is empty")
  @WithMockUser(username = CALLER, roles = "USER")
  void create_emptyDelta_returns422() throws Exception {
    when(createProposalUseCase.create(
            new CreateProposalCommand(
                CALLER_ID, CAMPAIGN_ID, "actor", TARGET_ID, SESSION_ID, Map.of())))
        .thenThrow(new EmptyDeltaException("empty"));

    mockMvc
        .perform(
            post("/api/v1/campaigns/{id}/proposals", CAMPAIGN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "targetType": "actor",
                      "targetId": "22222222-2222-2222-2222-222222222222",
                      "sessionId": "33333333-3333-3333-3333-333333333333",
                      "proposedDelta": {}
                    }
                    """))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EMPTY_DELTA"));
  }

  @Test
  @DisplayName("should return 404 PROPOSAL_TARGET_NOT_FOUND when the target is unknown")
  @WithMockUser(username = CALLER, roles = "USER")
  void create_unknownTarget_returns404() throws Exception {
    when(createProposalUseCase.create(
            new CreateProposalCommand(
                CALLER_ID,
                CAMPAIGN_ID,
                "actor",
                TARGET_ID,
                SESSION_ID,
                Map.of("name", "New Name"))))
        .thenThrow(new ProposalTargetNotFoundException("missing"));

    mockMvc
        .perform(
            post("/api/v1/campaigns/{id}/proposals", CAMPAIGN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_BODY))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errors[0].code").value("PROPOSAL_TARGET_NOT_FOUND"));
  }

  @Test
  @DisplayName("should return 409 CONCURRENT_PROPOSAL_EXISTS when an open proposal exists")
  @WithMockUser(username = CALLER, roles = "USER")
  void create_concurrent_returns409() throws Exception {
    when(createProposalUseCase.create(
            new CreateProposalCommand(
                CALLER_ID,
                CAMPAIGN_ID,
                "actor",
                TARGET_ID,
                SESSION_ID,
                Map.of("name", "New Name"))))
        .thenThrow(new ConcurrentProposalException("exists"));

    mockMvc
        .perform(
            post("/api/v1/campaigns/{id}/proposals", CAMPAIGN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_BODY))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("CONCURRENT_PROPOSAL_EXISTS"));
  }

  @Test
  @DisplayName("should return 401 when unauthenticated")
  void create_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/campaigns/{id}/proposals", CAMPAIGN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_BODY))
        .andExpect(status().isUnauthorized());
  }

  // -------------------------------------------------------------------------
  // GET /api/v1/campaigns/{id}/proposals
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("should return 200 with a paginated list and meta")
  @WithMockUser(username = CALLER, roles = "USER")
  void list_member_returns200() throws Exception {
    when(listProposalsUseCase.list(CAMPAIGN_ID, CALLER_ID, null, 0, 20))
        .thenReturn(new ProposalListView(List.of(view(ProposalStatus.OPEN)), 1L, 0, 20));

    mockMvc
        .perform(get("/api/v1/campaigns/{id}/proposals", CAMPAIGN_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].proposalId").value(PROPOSAL_ID.toString()))
        .andExpect(jsonPath("$.meta.totalCount").value(1))
        .andExpect(jsonPath("$.meta.page").value(0))
        .andExpect(jsonPath("$.meta.size").value(20));
  }

  @Test
  @DisplayName("should return 400 when the status filter is not a valid status")
  @WithMockUser(username = CALLER, roles = "USER")
  void list_invalidStatus_returns400() throws Exception {
    mockMvc
        .perform(get("/api/v1/campaigns/{id}/proposals", CAMPAIGN_ID).param("status", "bogus"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_PATH_PARAMETER"));
  }

  // -------------------------------------------------------------------------
  // POST /api/v1/campaigns/{id}/proposals/{pid}/votes
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("should return 200 with the cosigned proposal on co-sign")
  @WithMockUser(username = CALLER, roles = "USER")
  void coSign_valid_returns200() throws Exception {
    when(coSignProposalUseCase.coSign(
            new CoSignProposalCommand(CALLER_ID, CAMPAIGN_ID, PROPOSAL_ID)))
        .thenReturn(view(ProposalStatus.COSIGNED));

    mockMvc
        .perform(post("/api/v1/campaigns/{id}/proposals/{pid}/votes", CAMPAIGN_ID, PROPOSAL_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.proposalId").value(PROPOSAL_ID.toString()))
        .andExpect(jsonPath("$.data.status").value("COSIGNED"));
  }

  @Test
  @DisplayName("should return 422 AUTHOR_CANNOT_COSIGN when the author co-signs")
  @WithMockUser(username = CALLER, roles = "USER")
  void coSign_author_returns422() throws Exception {
    when(coSignProposalUseCase.coSign(
            new CoSignProposalCommand(CALLER_ID, CAMPAIGN_ID, PROPOSAL_ID)))
        .thenThrow(new AuthorCannotCoSignException("author"));

    mockMvc
        .perform(post("/api/v1/campaigns/{id}/proposals/{pid}/votes", CAMPAIGN_ID, PROPOSAL_ID))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("AUTHOR_CANNOT_COSIGN"));
  }

  @Test
  @DisplayName("should return 409 DUPLICATE_VOTE when the voter already voted")
  @WithMockUser(username = CALLER, roles = "USER")
  void coSign_duplicate_returns409() throws Exception {
    when(coSignProposalUseCase.coSign(
            new CoSignProposalCommand(CALLER_ID, CAMPAIGN_ID, PROPOSAL_ID)))
        .thenThrow(new DuplicateVoteException("dup"));

    mockMvc
        .perform(post("/api/v1/campaigns/{id}/proposals/{pid}/votes", CAMPAIGN_ID, PROPOSAL_ID))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("DUPLICATE_VOTE"));
  }

  @Test
  @DisplayName("should return 404 PROPOSAL_NOT_FOUND when the proposal is missing")
  @WithMockUser(username = CALLER, roles = "USER")
  void coSign_missing_returns404() throws Exception {
    when(coSignProposalUseCase.coSign(
            new CoSignProposalCommand(CALLER_ID, CAMPAIGN_ID, PROPOSAL_ID)))
        .thenThrow(new ProposalNotFoundException("missing"));

    mockMvc
        .perform(post("/api/v1/campaigns/{id}/proposals/{pid}/votes", CAMPAIGN_ID, PROPOSAL_ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errors[0].code").value("PROPOSAL_NOT_FOUND"));
  }

  private static ProposalView view(ProposalStatus status) {
    return new ProposalView(
        PROPOSAL_ID,
        CAMPAIGN_ID,
        ProposalTargetType.ACTOR,
        TARGET_ID,
        CALLER_ID,
        status,
        "{\"name\":\"New Name\"}",
        SESSION_ID,
        null,
        NOW.plusSeconds(2_592_000),
        NOW);
  }
}
