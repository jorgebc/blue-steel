package com.bluesteel.adapters.in.web.exploration;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bluesteel.BlueSteelApplication;
import com.bluesteel.application.model.worldstate.TimelineEntryView;
import com.bluesteel.application.model.worldstate.TimelineFilter;
import com.bluesteel.application.model.worldstate.TimelinePage;
import com.bluesteel.application.port.in.campaign.InviteCampaignMemberUseCase;
import com.bluesteel.application.port.in.health.CheckHealthUseCase;
import com.bluesteel.application.port.in.user.AdminBootstrapUseCase;
import com.bluesteel.application.port.in.user.ChangePasswordUseCase;
import com.bluesteel.application.port.in.user.GetCurrentUserUseCase;
import com.bluesteel.application.port.in.user.InvitePlatformUserUseCase;
import com.bluesteel.application.port.in.worldstate.GetTimelineUseCase;
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
@DisplayName("TimelineController")
class TimelineControllerTest {

  @MockitoBean private GetTimelineUseCase getTimelineUseCase;

  // Persistence-touching services whose JPA repositories are excluded from this sliced context —
  // mocked so the full application context loads (mirrors WorldStateExplorationControllerTest).
  @MockitoBean private CheckHealthUseCase checkHealthUseCase;
  @MockitoBean private AdminBootstrapUseCase adminBootstrapUseCase;
  @MockitoBean private InvitePlatformUserUseCase invitePlatformUserUseCase;
  @MockitoBean private InviteCampaignMemberUseCase inviteCampaignMemberUseCase;
  @MockitoBean private GetCurrentUserUseCase getCurrentUserUseCase;
  @MockitoBean private ChangePasswordUseCase changePasswordUseCase;

  @Autowired private WebApplicationContext context;
  private MockMvc mockMvc;

  private static final UUID CAMPAIGN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID EVENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID SESSION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final String CALLER = "00000000-0000-0000-0000-000000000001";
  private static final UUID CALLER_ID = UUID.fromString(CALLER);

  @BeforeEach
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  @DisplayName("should return 200 with the wrapped events array and the nextCursor meta")
  @WithMockUser(username = CALLER, roles = "USER")
  void getTimeline_returns200WithEventsAndNextCursor() throws Exception {
    TimelineEntryView entry =
        new TimelineEntryView(
            EVENT_ID,
            "Ambush at the Pass",
            "battle",
            List.of("Aldric", "Seraphine"),
            "Mountain Pass",
            SESSION_ID,
            1,
            Map.of("name", "Ambush at the Pass"),
            Instant.now());
    when(getTimelineUseCase.getTimeline(
            CAMPAIGN_ID, CALLER_ID, null, 20, new TimelineFilter(null, null, null)))
        .thenReturn(new TimelinePage(List.of(entry), "cursor-xyz"));

    mockMvc
        .perform(get("/api/v1/campaigns/{id}/timeline", CAMPAIGN_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.events[0].eventId").value(EVENT_ID.toString()))
        .andExpect(jsonPath("$.data.events[0].name").value("Ambush at the Pass"))
        .andExpect(jsonPath("$.data.events[0].eventType").value("battle"))
        .andExpect(jsonPath("$.data.events[0].involvedActorNames[0]").value("Aldric"))
        .andExpect(jsonPath("$.data.events[0].spaceName").value("Mountain Pass"))
        .andExpect(jsonPath("$.data.events[0].sessionSequenceNumber").value(1))
        .andExpect(jsonPath("$.meta.nextCursor").value("cursor-xyz"));
  }

  @Test
  @DisplayName("should return 200 with a null nextCursor on the final page")
  @WithMockUser(username = CALLER, roles = "USER")
  void getTimeline_returns200WithNullCursorOnFinalPage() throws Exception {
    when(getTimelineUseCase.getTimeline(
            CAMPAIGN_ID, CALLER_ID, null, 20, new TimelineFilter(null, null, null)))
        .thenReturn(new TimelinePage(List.of(), null));

    mockMvc
        .perform(get("/api/v1/campaigns/{id}/timeline", CAMPAIGN_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.events").isEmpty())
        .andExpect(jsonPath("$.meta.nextCursor").isEmpty());
  }

  @Test
  @DisplayName("should pass cursor, limit, and filter query params through to the use case")
  @WithMockUser(username = CALLER, roles = "USER")
  void getTimeline_passesQueryParamsThrough() throws Exception {
    when(getTimelineUseCase.getTimeline(
            CAMPAIGN_ID, CALLER_ID, "cursor-abc", 5, new TimelineFilter("ald", "pass", "battle")))
        .thenReturn(new TimelinePage(List.of(), null));

    mockMvc
        .perform(
            get("/api/v1/campaigns/{id}/timeline", CAMPAIGN_ID)
                .param("cursor", "cursor-abc")
                .param("limit", "5")
                .param("actor", "ald")
                .param("space", "pass")
                .param("eventType", "battle"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("should return 401 when requesting the timeline while unauthenticated")
  void getTimeline_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(get("/api/v1/campaigns/{id}/timeline", CAMPAIGN_ID))
        .andExpect(status().isUnauthorized());
  }
}
