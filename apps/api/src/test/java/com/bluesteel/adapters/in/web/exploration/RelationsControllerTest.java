package com.bluesteel.adapters.in.web.exploration;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bluesteel.BlueSteelApplication;
import com.bluesteel.application.model.worldstate.EntityVersionView;
import com.bluesteel.application.model.worldstate.RelationDetailView;
import com.bluesteel.application.model.worldstate.RelationSummaryView;
import com.bluesteel.application.port.in.campaign.InviteCampaignMemberUseCase;
import com.bluesteel.application.port.in.health.CheckHealthUseCase;
import com.bluesteel.application.port.in.user.AdminBootstrapUseCase;
import com.bluesteel.application.port.in.user.ChangePasswordUseCase;
import com.bluesteel.application.port.in.user.GetCurrentUserUseCase;
import com.bluesteel.application.port.in.user.InvitePlatformUserUseCase;
import com.bluesteel.application.port.in.worldstate.GetRelationDetailUseCase;
import com.bluesteel.application.port.in.worldstate.ListRelationsUseCase;
import com.bluesteel.domain.exception.EntityNotFoundException;
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
@DisplayName("RelationsController")
class RelationsControllerTest {

  @MockitoBean private ListRelationsUseCase listRelationsUseCase;
  @MockitoBean private GetRelationDetailUseCase getRelationDetailUseCase;

  @MockitoBean private CheckHealthUseCase checkHealthUseCase;
  @MockitoBean private AdminBootstrapUseCase adminBootstrapUseCase;
  @MockitoBean private InvitePlatformUserUseCase invitePlatformUserUseCase;
  @MockitoBean private InviteCampaignMemberUseCase inviteCampaignMemberUseCase;
  @MockitoBean private GetCurrentUserUseCase getCurrentUserUseCase;
  @MockitoBean private ChangePasswordUseCase changePasswordUseCase;

  @Autowired private WebApplicationContext context;
  private MockMvc mockMvc;

  private static final UUID CAMPAIGN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID RELATION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID SOURCE_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
  private static final UUID TARGET_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
  private static final UUID SESSION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final String CALLER = "00000000-0000-0000-0000-000000000001";
  private static final UUID CALLER_ID = UUID.fromString(CALLER);

  @BeforeEach
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  @DisplayName("should return 200 with relation summaries carrying graph endpoints")
  @WithMockUser(username = CALLER, roles = "USER")
  void listRelations_returns200WithEndpoints() throws Exception {
    RelationSummaryView summary =
        new RelationSummaryView(
            RELATION_ID,
            "Mira guides the party",
            "alliance",
            SOURCE_ID,
            "actor",
            TARGET_ID,
            "space",
            SESSION_ID,
            Instant.now());
    when(listRelationsUseCase.list(CAMPAIGN_ID, CALLER_ID, null)).thenReturn(List.of(summary));

    mockMvc
        .perform(get("/api/v1/campaigns/{id}/relations", CAMPAIGN_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].relationId").value(RELATION_ID.toString()))
        .andExpect(jsonPath("$.data[0].name").value("Mira guides the party"))
        .andExpect(jsonPath("$.data[0].kind").value("alliance"))
        .andExpect(jsonPath("$.data[0].sourceEntityId").value(SOURCE_ID.toString()))
        .andExpect(jsonPath("$.data[0].sourceEntityType").value("actor"))
        .andExpect(jsonPath("$.data[0].targetEntityId").value(TARGET_ID.toString()))
        .andExpect(jsonPath("$.data[0].targetEntityType").value("space"));
  }

  @Test
  @DisplayName("should pass the actor query param through as the endpoint filter")
  @WithMockUser(username = CALLER, roles = "USER")
  void listRelations_actorFilter_passedThrough() throws Exception {
    when(listRelationsUseCase.list(CAMPAIGN_ID, CALLER_ID, SOURCE_ID)).thenReturn(List.of());

    mockMvc
        .perform(
            get("/api/v1/campaigns/{id}/relations", CAMPAIGN_ID)
                .param("actor", SOURCE_ID.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty());
  }

  @Test
  @DisplayName("should return 200 with the relation's endpoints and ordered version history")
  @WithMockUser(username = CALLER, roles = "USER")
  void getRelation_returns200WithHistory() throws Exception {
    EntityVersionView v1 =
        new EntityVersionView(
            UUID.randomUUID(), 1, SESSION_ID, 1, Map.of(), Map.of("name", "x"), Instant.now());
    RelationDetailView detail =
        new RelationDetailView(
            RELATION_ID,
            "Mira guides the party",
            "alliance",
            SOURCE_ID,
            "actor",
            TARGET_ID,
            "space",
            CALLER_ID,
            Instant.now(),
            List.of(v1));
    when(getRelationDetailUseCase.getDetail(CAMPAIGN_ID, RELATION_ID, CALLER_ID))
        .thenReturn(detail);

    mockMvc
        .perform(get("/api/v1/campaigns/{id}/relations/{rid}", CAMPAIGN_ID, RELATION_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.relationId").value(RELATION_ID.toString()))
        .andExpect(jsonPath("$.data.sourceEntityId").value(SOURCE_ID.toString()))
        .andExpect(jsonPath("$.data.versions[0].versionNumber").value(1));
  }

  @Test
  @DisplayName("should return 404 with ENTITY_NOT_FOUND when the relation does not exist")
  @WithMockUser(username = CALLER, roles = "USER")
  void getRelation_notFound_returns404() throws Exception {
    when(getRelationDetailUseCase.getDetail(CAMPAIGN_ID, RELATION_ID, CALLER_ID))
        .thenThrow(new EntityNotFoundException("relation not found: " + RELATION_ID));

    mockMvc
        .perform(get("/api/v1/campaigns/{id}/relations/{rid}", CAMPAIGN_ID, RELATION_ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errors[0].code").value("ENTITY_NOT_FOUND"));
  }

  @Test
  @DisplayName("should return 401 when listing relations while unauthenticated")
  void listRelations_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(get("/api/v1/campaigns/{id}/relations", CAMPAIGN_ID))
        .andExpect(status().isUnauthorized());
  }
}
