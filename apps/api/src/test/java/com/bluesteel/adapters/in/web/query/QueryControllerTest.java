package com.bluesteel.adapters.in.web.query;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bluesteel.BlueSteelApplication;
import com.bluesteel.application.model.query.Citation;
import com.bluesteel.application.model.query.QueryHistoryView;
import com.bluesteel.application.model.query.QueryLogEntry;
import com.bluesteel.application.model.query.QueryResponse;
import com.bluesteel.application.model.query.QueryUsage;
import com.bluesteel.application.port.in.campaign.InviteCampaignMemberUseCase;
import com.bluesteel.application.port.in.health.CheckHealthUseCase;
import com.bluesteel.application.port.in.query.AnswerQueryUseCase;
import com.bluesteel.application.port.in.query.GetQueryHistoryUseCase;
import com.bluesteel.application.port.in.query.GetQueryUsageUseCase;
import com.bluesteel.application.port.in.user.AdminBootstrapUseCase;
import com.bluesteel.application.port.in.user.ChangePasswordUseCase;
import com.bluesteel.application.port.in.user.GetCurrentUserUseCase;
import com.bluesteel.application.port.in.user.InvitePlatformUserUseCase;
import com.bluesteel.domain.exception.QueryResponseParseException;
import com.bluesteel.domain.exception.QueryTimeoutException;
import com.bluesteel.domain.exception.RateLimitExceededException;
import com.bluesteel.domain.exception.TokenBudgetExceededException;
import com.bluesteel.domain.exception.UnauthorizedException;
import java.time.Instant;
import java.util.List;
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
@DisplayName("QueryController")
class QueryControllerTest {

  @MockitoBean private AnswerQueryUseCase answerQueryUseCase;
  @MockitoBean private GetQueryUsageUseCase getQueryUsageUseCase;
  @MockitoBean private GetQueryHistoryUseCase getQueryHistoryUseCase;
  @MockitoBean private QueryRateLimiter rateLimiter;

  // Mocked so the full application context loads without database/infra-backed beans.
  @MockitoBean private CheckHealthUseCase checkHealthUseCase;
  @MockitoBean private AdminBootstrapUseCase adminBootstrapUseCase;
  @MockitoBean private InvitePlatformUserUseCase invitePlatformUserUseCase;
  @MockitoBean private InviteCampaignMemberUseCase inviteCampaignMemberUseCase;
  @MockitoBean private GetCurrentUserUseCase getCurrentUserUseCase;
  @MockitoBean private ChangePasswordUseCase changePasswordUseCase;

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  private static final UUID CAMPAIGN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID SESSION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID CALLER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final String QUESTION = "Who is Mira?";

  @BeforeEach
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  @DisplayName("should return 200 with the answer and citations envelope on success")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void ask_validQuestion_returns200() throws Exception {
    QueryResponse result =
        new QueryResponse(
            "Mira is a rogue.", List.of(new Citation(SESSION_ID, 4, "Mira joined the party.")));
    when(answerQueryUseCase.answer(CAMPAIGN_ID, CALLER_ID, QUESTION)).thenReturn(result);

    mockMvc
        .perform(
            post("/api/v1/campaigns/{id}/queries", CAMPAIGN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "question": "Who is Mira?" }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.answer").value("Mira is a rogue."))
        .andExpect(jsonPath("$.data.citations[0].sessionId").value(SESSION_ID.toString()))
        .andExpect(jsonPath("$.data.citations[0].sequenceNumber").value(4))
        .andExpect(jsonPath("$.data.citations[0].snippet").value("Mira joined the party."))
        .andExpect(jsonPath("$.errors").isEmpty());
  }

  @Test
  @DisplayName("should return 504 QUERY_TIMEOUT when the query exceeds its deadline")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void ask_timeout_returns504() throws Exception {
    when(answerQueryUseCase.answer(CAMPAIGN_ID, CALLER_ID, QUESTION))
        .thenThrow(new QueryTimeoutException());

    mockMvc
        .perform(
            post("/api/v1/campaigns/{id}/queries", CAMPAIGN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "question": "Who is Mira?" }
                    """))
        .andExpect(status().isGatewayTimeout())
        .andExpect(jsonPath("$.errors[0].code").value("QUERY_TIMEOUT"));
  }

  @Test
  @DisplayName("should return 429 QUERY_RATE_LIMITED when the caller exceeds the rate limit")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void ask_rateLimited_returns429() throws Exception {
    doThrow(new RateLimitExceededException()).when(rateLimiter).check(CALLER_ID, CAMPAIGN_ID);

    mockMvc
        .perform(
            post("/api/v1/campaigns/{id}/queries", CAMPAIGN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "question": "Who is Mira?" }
                    """))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.errors[0].code").value("QUERY_RATE_LIMITED"));
  }

  @Test
  @DisplayName("should return 422 QUERY_TOKEN_BUDGET_EXCEEDED when the prompt exceeds the budget")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void ask_tokenBudgetExceeded_returns422() throws Exception {
    when(answerQueryUseCase.answer(CAMPAIGN_ID, CALLER_ID, QUESTION))
        .thenThrow(new TokenBudgetExceededException(9000, 6000));

    mockMvc
        .perform(
            post("/api/v1/campaigns/{id}/queries", CAMPAIGN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "question": "Who is Mira?" }
                    """))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("QUERY_TOKEN_BUDGET_EXCEEDED"));
  }

  @Test
  @DisplayName("should return 502 QUERY_ANSWER_UNPARSEABLE when the LLM response cannot be parsed")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void ask_unparseableAnswer_returns502() throws Exception {
    when(answerQueryUseCase.answer(CAMPAIGN_ID, CALLER_ID, QUESTION))
        .thenThrow(
            new QueryResponseParseException(
                "Unparseable LLM query response", new RuntimeException()));

    mockMvc
        .perform(
            post("/api/v1/campaigns/{id}/queries", CAMPAIGN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "question": "Who is Mira?" }
                    """))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.errors[0].code").value("QUERY_ANSWER_UNPARSEABLE"));
  }

  @Test
  @DisplayName("should return 400 when the question is blank")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void ask_blankQuestion_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/campaigns/{id}/queries", CAMPAIGN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "question": "" }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VALIDATION_ERROR"));
  }

  @Test
  @DisplayName("should return 401 when the request is unauthenticated")
  void ask_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/campaigns/{id}/queries", CAMPAIGN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "question": "Who is Mira?" }
                    """))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName(
      "should return 200 with the shared budget and caller's remaining rate-limit headroom")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void usage_authenticated_returns200() throws Exception {
    when(getQueryUsageUseCase.currentUsage()).thenReturn(new QueryUsage(0.42, 1.00));
    when(rateLimiter.remaining(CALLER_ID, CAMPAIGN_ID)).thenReturn(7);
    when(rateLimiter.maxRequests()).thenReturn(10);
    when(rateLimiter.windowSeconds()).thenReturn(60L);

    mockMvc
        .perform(get("/api/v1/campaigns/{id}/queries/usage", CAMPAIGN_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.consumedUsd").value(0.42))
        .andExpect(jsonPath("$.data.capUsd").value(1.00))
        .andExpect(jsonPath("$.data.requestsRemaining").value(7))
        .andExpect(jsonPath("$.data.maxRequests").value(10))
        .andExpect(jsonPath("$.data.windowSeconds").value(60))
        .andExpect(jsonPath("$.errors").isEmpty());
  }

  @Test
  @DisplayName("should return 401 when usage is requested unauthenticated")
  void usage_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(get("/api/v1/campaigns/{id}/queries/usage", CAMPAIGN_ID))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("should return 200 with the history entries and pagination meta for a member")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void history_member_returns200() throws Exception {
    QueryLogEntry entry =
        new QueryLogEntry(
            UUID.fromString("33333333-3333-3333-3333-333333333333"),
            CAMPAIGN_ID,
            CALLER_ID,
            "Who is Mira?",
            "Mira is a rogue.",
            List.of(new Citation(SESSION_ID, 4, "Mira joined the party.")),
            Instant.parse("2026-06-17T10:00:00Z"));
    when(getQueryHistoryUseCase.getHistory(CAMPAIGN_ID, CALLER_ID, 0, 20))
        .thenReturn(new QueryHistoryView(List.of(entry), 1L, 0, 20));

    mockMvc
        .perform(get("/api/v1/campaigns/{id}/queries/history", CAMPAIGN_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].question").value("Who is Mira?"))
        .andExpect(jsonPath("$.data[0].answer").value("Mira is a rogue."))
        .andExpect(jsonPath("$.data[0].citations[0].sessionId").value(SESSION_ID.toString()))
        .andExpect(jsonPath("$.data[0].citations[0].sequenceNumber").value(4))
        .andExpect(jsonPath("$.meta.page").value(0))
        .andExpect(jsonPath("$.meta.size").value(20))
        .andExpect(jsonPath("$.meta.totalCount").value(1))
        .andExpect(jsonPath("$.errors").isEmpty());
  }

  @Test
  @DisplayName("should return 403 when a non-member requests the history")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void history_nonMember_returns403() throws Exception {
    when(getQueryHistoryUseCase.getHistory(CAMPAIGN_ID, CALLER_ID, 0, 20))
        .thenThrow(new UnauthorizedException("Caller is not a member of this campaign"));

    mockMvc
        .perform(get("/api/v1/campaigns/{id}/queries/history", CAMPAIGN_ID))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errors[0].code").value("FORBIDDEN"));
  }

  @Test
  @DisplayName("should return 401 when history is requested unauthenticated")
  void history_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(get("/api/v1/campaigns/{id}/queries/history", CAMPAIGN_ID))
        .andExpect(status().isUnauthorized());
  }
}
