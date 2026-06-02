package com.bluesteel.adapters.in.web.exploration;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bluesteel.BlueSteelApplication;
import com.bluesteel.application.model.worldstate.EntityDetailView;
import com.bluesteel.application.model.worldstate.EntityListPage;
import com.bluesteel.application.model.worldstate.EntitySummaryView;
import com.bluesteel.application.model.worldstate.EntityVersionView;
import com.bluesteel.application.port.in.campaign.InviteCampaignMemberUseCase;
import com.bluesteel.application.port.in.health.CheckHealthUseCase;
import com.bluesteel.application.port.in.user.AdminBootstrapUseCase;
import com.bluesteel.application.port.in.user.ChangePasswordUseCase;
import com.bluesteel.application.port.in.user.GetCurrentUserUseCase;
import com.bluesteel.application.port.in.user.InvitePlatformUserUseCase;
import com.bluesteel.application.port.in.worldstate.GetEntityDetailUseCase;
import com.bluesteel.application.port.in.worldstate.ListEntitiesUseCase;
import com.bluesteel.domain.exception.EntityNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
@DisplayName("WorldStateExplorationController")
class WorldStateExplorationControllerTest {

  @MockitoBean private ListEntitiesUseCase listEntitiesUseCase;
  @MockitoBean private GetEntityDetailUseCase getEntityDetailUseCase;

  // Persistence-touching services whose JPA repositories are excluded from this sliced context —
  // mocked so the full application context loads (mirrors HealthControllerTest).
  @MockitoBean private CheckHealthUseCase checkHealthUseCase;
  @MockitoBean private AdminBootstrapUseCase adminBootstrapUseCase;
  @MockitoBean private InvitePlatformUserUseCase invitePlatformUserUseCase;
  @MockitoBean private InviteCampaignMemberUseCase inviteCampaignMemberUseCase;
  @MockitoBean private GetCurrentUserUseCase getCurrentUserUseCase;
  @MockitoBean private ChangePasswordUseCase changePasswordUseCase;

  @org.springframework.beans.factory.annotation.Autowired private WebApplicationContext context;
  private MockMvc mockMvc;

  private static final UUID CAMPAIGN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID ACTOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID SESSION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final String CALLER = "00000000-0000-0000-0000-000000000001";
  private static final UUID CALLER_ID = UUID.fromString(CALLER);

  @BeforeEach
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  @DisplayName("should return 200 with actor summaries and pagination meta on list")
  @WithMockUser(username = CALLER, roles = "USER")
  void listActors_returns200WithMeta() throws Exception {
    EntitySummaryView summary =
        new EntitySummaryView(
            ACTOR_ID, "actor", "Aldric", 2, Map.of("role", "knight"), SESSION_ID, Instant.now());
    when(listEntitiesUseCase.list("actor", CAMPAIGN_ID, CALLER_ID, 0, 20))
        .thenReturn(new EntityListPage(List.of(summary), 0, 20, 1L));

    mockMvc
        .perform(get("/api/v1/campaigns/{id}/actors", CAMPAIGN_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].entityId").value(ACTOR_ID.toString()))
        .andExpect(jsonPath("$.data[0].entityType").value("actor"))
        .andExpect(jsonPath("$.data[0].name").value("Aldric"))
        .andExpect(jsonPath("$.data[0].latestVersionNumber").value(2))
        .andExpect(jsonPath("$.data[0].currentSnapshot.role").value("knight"))
        .andExpect(jsonPath("$.meta.page").value(0))
        .andExpect(jsonPath("$.meta.size").value(20))
        .andExpect(jsonPath("$.meta.totalCount").value(1));
  }

  @Test
  @DisplayName("should return 200 with the actor's ordered version history on detail")
  @WithMockUser(username = CALLER, roles = "USER")
  void getActor_returns200WithHistory() throws Exception {
    EntityVersionView v1 =
        new EntityVersionView(
            UUID.randomUUID(), 1, SESSION_ID, 1, Map.of(), Map.of("role", "squire"), Instant.now());
    EntityDetailView detail =
        new EntityDetailView(ACTOR_ID, "actor", "Aldric", CALLER_ID, Instant.now(), List.of(v1));
    when(getEntityDetailUseCase.getDetail("actor", CAMPAIGN_ID, ACTOR_ID, CALLER_ID))
        .thenReturn(detail);

    mockMvc
        .perform(get("/api/v1/campaigns/{id}/actors/{aid}", CAMPAIGN_ID, ACTOR_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.entityId").value(ACTOR_ID.toString()))
        .andExpect(jsonPath("$.data.name").value("Aldric"))
        .andExpect(jsonPath("$.data.versions[0].versionNumber").value(1))
        .andExpect(jsonPath("$.data.versions[0].sessionSequenceNumber").value(1))
        .andExpect(jsonPath("$.data.versions[0].fullSnapshot.role").value("squire"));
  }

  @Test
  @DisplayName("should return 404 with ENTITY_NOT_FOUND when the entity does not exist")
  @WithMockUser(username = CALLER, roles = "USER")
  void getActor_notFound_returns404() throws Exception {
    when(getEntityDetailUseCase.getDetail("actor", CAMPAIGN_ID, ACTOR_ID, CALLER_ID))
        .thenThrow(new EntityNotFoundException("actor not found: " + ACTOR_ID));

    mockMvc
        .perform(get("/api/v1/campaigns/{id}/actors/{aid}", CAMPAIGN_ID, ACTOR_ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errors[0].code").value("ENTITY_NOT_FOUND"));
  }

  @Test
  @DisplayName("should return 200 with space summaries on the spaces list")
  @WithMockUser(username = CALLER, roles = "USER")
  void listSpaces_returns200() throws Exception {
    EntitySummaryView summary =
        new EntitySummaryView(
            UUID.randomUUID(), "space", "The Tavern", 1, Map.of(), SESSION_ID, Instant.now());
    when(listEntitiesUseCase.list("space", CAMPAIGN_ID, CALLER_ID, 0, 20))
        .thenReturn(new EntityListPage(List.of(summary), 0, 20, 1L));

    mockMvc
        .perform(get("/api/v1/campaigns/{id}/spaces", CAMPAIGN_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].entityType").value("space"))
        .andExpect(jsonPath("$.data[0].name").value("The Tavern"));
  }

  @Test
  @DisplayName("should return 401 when listing actors while unauthenticated")
  void listActors_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(get("/api/v1/campaigns/{id}/actors", CAMPAIGN_ID))
        .andExpect(status().isUnauthorized());
  }
}
