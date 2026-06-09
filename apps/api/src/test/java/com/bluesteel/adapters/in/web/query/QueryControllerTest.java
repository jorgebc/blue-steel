package com.bluesteel.adapters.in.web.query;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bluesteel.BlueSteelApplication;
import com.bluesteel.application.model.query.Citation;
import com.bluesteel.application.model.query.QueryResponse;
import com.bluesteel.application.port.in.campaign.InviteCampaignMemberUseCase;
import com.bluesteel.application.port.in.health.CheckHealthUseCase;
import com.bluesteel.application.port.in.query.AnswerQueryUseCase;
import com.bluesteel.application.port.in.user.AdminBootstrapUseCase;
import com.bluesteel.application.port.in.user.ChangePasswordUseCase;
import com.bluesteel.application.port.in.user.GetCurrentUserUseCase;
import com.bluesteel.application.port.in.user.InvitePlatformUserUseCase;
import com.bluesteel.domain.exception.QueryTimeoutException;
import com.bluesteel.domain.exception.RateLimitExceededException;
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
}
