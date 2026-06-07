package com.bluesteel.adapters.in.web.exploration;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bluesteel.BlueSteelApplication;
import com.bluesteel.application.model.session.SessionSummaryView;
import com.bluesteel.application.model.worldstate.EntityDetailView;
import com.bluesteel.application.model.worldstate.EntityLinks;
import com.bluesteel.application.model.worldstate.EntityListPage;
import com.bluesteel.application.model.worldstate.EntitySummaryView;
import com.bluesteel.application.model.worldstate.EntityVersionView;
import com.bluesteel.application.model.worldstate.RelationSummaryView;
import com.bluesteel.application.model.worldstate.TimelineEntryView;
import com.bluesteel.application.port.in.campaign.InviteCampaignMemberUseCase;
import com.bluesteel.application.port.in.health.CheckHealthUseCase;
import com.bluesteel.application.port.in.user.AdminBootstrapUseCase;
import com.bluesteel.application.port.in.user.ChangePasswordUseCase;
import com.bluesteel.application.port.in.user.GetCurrentUserUseCase;
import com.bluesteel.application.port.in.user.InvitePlatformUserUseCase;
import com.bluesteel.application.port.in.worldstate.GetEntityDetailUseCase;
import com.bluesteel.application.port.in.worldstate.GetEntityLinksUseCase;
import com.bluesteel.application.port.in.worldstate.ListEntitiesUseCase;
import com.bluesteel.domain.exception.EntityNotFoundException;
import com.bluesteel.domain.session.SessionStatus;
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
  @MockitoBean private GetEntityLinksUseCase getEntityLinksUseCase;

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
  @DisplayName(
      "should return 200 with the actor's relations, related entities, events and sessions")
  @WithMockUser(username = CALLER, roles = "USER")
  void getActorLinks_returns200WithAllSections() throws Exception {
    UUID relationId = UUID.fromString("44444444-4444-4444-4444-444444444444");
    UUID spaceId = UUID.fromString("55555555-5555-5555-5555-555555555555");
    UUID eventId = UUID.fromString("66666666-6666-6666-6666-666666666666");
    RelationSummaryView relation =
        new RelationSummaryView(
            relationId,
            "Aldric guards the Tavern",
            "guardianship",
            ACTOR_ID,
            "actor",
            spaceId,
            "space",
            SESSION_ID,
            Instant.now());
    EntitySummaryView related =
        new EntitySummaryView(
            spaceId, "space", "The Tavern", 1, Map.of(), SESSION_ID, Instant.now());
    TimelineEntryView event =
        new TimelineEntryView(
            eventId,
            "The Brawl",
            "conflict",
            List.of("Aldric"),
            "The Tavern",
            SESSION_ID,
            1,
            Map.of(),
            Instant.now());
    SessionSummaryView appearance =
        new SessionSummaryView(
            SESSION_ID, SessionStatus.COMMITTED, 7, Instant.now(), Instant.now());
    EntityLinks links =
        new EntityLinks(List.of(relation), List.of(related), List.of(event), List.of(appearance));
    when(getEntityLinksUseCase.getLinks("actor", CAMPAIGN_ID, ACTOR_ID, CALLER_ID))
        .thenReturn(links);

    mockMvc
        .perform(get("/api/v1/campaigns/{id}/actors/{aid}/links", CAMPAIGN_ID, ACTOR_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.relations[0].relationId").value(relationId.toString()))
        .andExpect(jsonPath("$.data.relations[0].kind").value("guardianship"))
        .andExpect(jsonPath("$.data.relatedEntities[0].entityId").value(spaceId.toString()))
        .andExpect(jsonPath("$.data.relatedEntities[0].name").value("The Tavern"))
        .andExpect(jsonPath("$.data.events[0].eventId").value(eventId.toString()))
        .andExpect(jsonPath("$.data.events[0].eventType").value("conflict"))
        .andExpect(jsonPath("$.data.appearances[0].sessionId").value(SESSION_ID.toString()))
        .andExpect(jsonPath("$.data.appearances[0].sequenceNumber").value(7))
        .andExpect(jsonPath("$.data.appearances[0].status").value("COMMITTED"));
  }

  @Test
  @DisplayName("should return 200 with empty link sections for a space with no cross-links")
  @WithMockUser(username = CALLER, roles = "USER")
  void getSpaceLinks_returns200WithEmptySections() throws Exception {
    UUID spaceId = UUID.fromString("55555555-5555-5555-5555-555555555555");
    when(getEntityLinksUseCase.getLinks("space", CAMPAIGN_ID, spaceId, CALLER_ID))
        .thenReturn(new EntityLinks(List.of(), List.of(), List.of(), List.of()));

    mockMvc
        .perform(get("/api/v1/campaigns/{id}/spaces/{sid}/links", CAMPAIGN_ID, spaceId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.relations").isEmpty())
        .andExpect(jsonPath("$.data.relatedEntities").isEmpty())
        .andExpect(jsonPath("$.data.events").isEmpty())
        .andExpect(jsonPath("$.data.appearances").isEmpty());
  }

  @Test
  @DisplayName("should return 401 when listing actors while unauthenticated")
  void listActors_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(get("/api/v1/campaigns/{id}/actors", CAMPAIGN_ID))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("should return 401 when reading actor links while unauthenticated")
  void getActorLinks_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(get("/api/v1/campaigns/{id}/actors/{aid}/links", CAMPAIGN_ID, ACTOR_ID))
        .andExpect(status().isUnauthorized());
  }
}
