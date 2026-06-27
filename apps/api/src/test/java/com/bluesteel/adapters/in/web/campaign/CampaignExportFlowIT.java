package com.bluesteel.adapters.in.web.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bluesteel.BlueSteelApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * End-to-end seam IT for campaign export (F7.1, D-112): exercises the wired path the isolated
 * slices cannot prove together. The controller unit test mocks the use case, and the persistence IT
 * never goes through HTTP/security — neither proves that {@code GET /api/v1/campaigns/{id}/export}
 * streams a real, parseable archive through Spring security + DB, nor that the authz/cap/not-found
 * paths surface as the standard error envelope. The cap is pinned to 1 here so the {@code 422
 * EXPORT_TOO_LARGE} path is reachable without bulk seeding; the GM and admin happy paths therefore
 * seed exactly one entity.
 */
@ActiveProfiles("local")
@SpringBootTest(
    classes = BlueSteelApplication.class,
    webEnvironment = WebEnvironment.MOCK,
    properties = {
      "spring.jpa.hibernate.ddl-auto=none",
      "admin.email=bootstrap-test@example.com",
      "admin.password=Bootstrap!Test123456",
      "jwt.secret=test-jwt-secret-test-jwt-secret-test-jwt-secret!",
      "campaign.export.max-entities=1"
    })
@Transactional
@DisplayName("Campaign export flow (download / authz / cap) over real HTTP + DB")
class CampaignExportFlowIT {

  static final PostgreSQLContainer<?> postgres;

  static {
    postgres =
        new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("bluesteel_test")
            .withUsername("test")
            .withPassword("test");
    postgres.start();
  }

  @DynamicPropertySource
  static void configureDataSource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired private WebApplicationContext context;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ObjectMapper objectMapper;

  private MockMvc mockMvc;

  private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID GM_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
  private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c3");
  private static final UUID CAMPAIGN_ID = UUID.fromString("11111111-1111-1111-1111-1111111111e7");
  private static final UUID EMPTY_CAMPAIGN_ID =
      UUID.fromString("11111111-1111-1111-1111-1111111111e0");

  @BeforeEach
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  @DisplayName("should stream a parseable JSON archive attachment for the GM (200)")
  @WithMockUser(username = "00000000-0000-0000-0000-0000000000b2", roles = "USER")
  void export_asGm_streamsParseableArchiveAttachment() throws Exception {
    insertUser(GM_ID);
    insertCampaign(CAMPAIGN_ID, GM_ID, "Curse of Strahd");
    insertMember(CAMPAIGN_ID, GM_ID, "gm");
    UUID sessionId = insertSession(CAMPAIGN_ID, GM_ID, 1, "committed");
    UUID aldricId = insertEntity("actors", CAMPAIGN_ID, GM_ID, sessionId, "Aldric");
    insertVersion(
        "actor_versions", "actor_id", aldricId, sessionId, 1, null, "{\"name\":\"Aldric\"}");
    insertVersion(
        "actor_versions",
        "actor_id",
        aldricId,
        sessionId,
        2,
        "{\"role\":\"knight\"}",
        "{\"name\":\"Aldric\",\"role\":\"knight\"}");
    insertAnnotation(CAMPAIGN_ID, "actor", aldricId, GM_ID, "A loyal squire");

    MvcResult started =
        mockMvc
            .perform(get("/api/v1/campaigns/{id}/export", CAMPAIGN_ID))
            .andExpect(request().asyncStarted())
            .andReturn();

    String body =
        mockMvc
            .perform(asyncDispatch(started))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(
                header()
                    .string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"curse-of-strahd-export.json\""))
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode archive = objectMapper.readTree(body);
    assertThat(archive.get("schemaVersion").asText()).isEqualTo("1");
    assertThat(archive.get("exportedAt").asText()).isNotBlank();
    assertThat(archive.at("/campaign/id").asText()).isEqualTo(CAMPAIGN_ID.toString());
    assertThat(archive.at("/campaign/name").asText()).isEqualTo("Curse of Strahd");
    assertThat(archive.get("members")).hasSize(1);
    assertThat(archive.get("entities")).hasSize(1);
    assertThat(archive.at("/entities/0/versions")).hasSize(2);
    assertThat(archive.get("annotations")).hasSize(1);
    assertThat(archive.get("sessions")).hasSize(1);
  }

  @Test
  @DisplayName("should stream an archive with empty collections for an empty campaign (200)")
  @WithMockUser(username = "00000000-0000-0000-0000-0000000000b2", roles = "USER")
  void export_emptyCampaign_streamsEmptyCollections() throws Exception {
    insertUser(GM_ID);
    insertCampaign(EMPTY_CAMPAIGN_ID, GM_ID, "Empty");
    insertMember(EMPTY_CAMPAIGN_ID, GM_ID, "gm");

    MvcResult started =
        mockMvc
            .perform(get("/api/v1/campaigns/{id}/export", EMPTY_CAMPAIGN_ID))
            .andExpect(request().asyncStarted())
            .andReturn();

    String body =
        mockMvc
            .perform(asyncDispatch(started))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode archive = objectMapper.readTree(body);
    assertThat(archive.get("members")).hasSize(1);
    assertThat(archive.get("entities")).isEmpty();
    assertThat(archive.get("annotations")).isEmpty();
    assertThat(archive.get("sessions")).isEmpty();
  }

  @Test
  @DisplayName("should let an admin who is not a member export the campaign (200)")
  @WithMockUser(username = "00000000-0000-0000-0000-0000000000a1", roles = "ADMIN")
  void export_asNonMemberAdmin_streamsArchive() throws Exception {
    insertUser(ADMIN_ID);
    insertUser(GM_ID);
    insertCampaign(CAMPAIGN_ID, GM_ID, "Admin Export");
    insertMember(CAMPAIGN_ID, GM_ID, "gm");

    MvcResult started =
        mockMvc
            .perform(get("/api/v1/campaigns/{id}/export", CAMPAIGN_ID))
            .andExpect(request().asyncStarted())
            .andReturn();

    mockMvc.perform(asyncDispatch(started)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("should reject a campaign over the entity cap with 422 EXPORT_TOO_LARGE")
  @WithMockUser(username = "00000000-0000-0000-0000-0000000000b2", roles = "USER")
  void export_overCap_returns422() throws Exception {
    insertUser(GM_ID);
    insertCampaign(CAMPAIGN_ID, GM_ID, "Too Large");
    insertMember(CAMPAIGN_ID, GM_ID, "gm");
    UUID sessionId = insertSession(CAMPAIGN_ID, GM_ID, 1, "committed");
    // Two entities exceed the max-entities=1 cap pinned for this class.
    UUID a = insertEntity("actors", CAMPAIGN_ID, GM_ID, sessionId, "Aldric");
    insertVersion("actor_versions", "actor_id", a, sessionId, 1, null, "{\"name\":\"Aldric\"}");
    UUID b = insertEntity("spaces", CAMPAIGN_ID, GM_ID, sessionId, "Tavern");
    insertVersion("space_versions", "space_id", b, sessionId, 1, null, "{\"name\":\"Tavern\"}");

    mockMvc
        .perform(get("/api/v1/campaigns/{id}/export", CAMPAIGN_ID))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EXPORT_TOO_LARGE"));
  }

  @Test
  @DisplayName("should return 404 CAMPAIGN_NOT_FOUND for an unknown campaign")
  @WithMockUser(username = "00000000-0000-0000-0000-0000000000b2", roles = "USER")
  void export_unknownCampaign_returns404() throws Exception {
    insertUser(GM_ID);

    mockMvc
        .perform(get("/api/v1/campaigns/{id}/export", UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errors[0].code").value("CAMPAIGN_NOT_FOUND"));
  }

  @Test
  @DisplayName("should return 403 FORBIDDEN for a non-GM member (player)")
  @WithMockUser(username = "00000000-0000-0000-0000-0000000000c3", roles = "USER")
  void export_asPlayerMember_returns403() throws Exception {
    insertUser(GM_ID);
    insertUser(PLAYER_ID);
    insertCampaign(CAMPAIGN_ID, GM_ID, "Forbidden");
    insertMember(CAMPAIGN_ID, GM_ID, "gm");
    insertMember(CAMPAIGN_ID, PLAYER_ID, "player");

    mockMvc
        .perform(get("/api/v1/campaigns/{id}/export", CAMPAIGN_ID))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errors[0].code").value("FORBIDDEN"));
  }

  @Test
  @DisplayName("should return 401 for an unauthenticated request")
  void export_unauthenticated_returns401() throws Exception {
    insertUser(GM_ID);
    insertCampaign(CAMPAIGN_ID, GM_ID, "Unauthenticated");
    insertMember(CAMPAIGN_ID, GM_ID, "gm");

    mockMvc
        .perform(get("/api/v1/campaigns/{id}/export", CAMPAIGN_ID))
        .andExpect(status().isUnauthorized());
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private void insertUser(UUID id) {
    jdbcTemplate.update(
        "INSERT INTO users (id, email, password_hash, is_admin, force_password_change, created_at)"
            + " VALUES (?, ?, 'hash', FALSE, FALSE, now())",
        id,
        id + "@test.com");
  }

  private void insertCampaign(UUID id, UUID createdBy, String name) {
    jdbcTemplate.update(
        "INSERT INTO campaigns (id, name, created_by, content_language, created_at)"
            + " VALUES (?, ?, ?, 'en', now())",
        id,
        name,
        createdBy);
  }

  private void insertMember(UUID campaignId, UUID userId, String role) {
    jdbcTemplate.update(
        "INSERT INTO campaign_members (id, campaign_id, user_id, role, joined_at)"
            + " VALUES (?, ?, ?, ?, now())",
        UUID.randomUUID(),
        campaignId,
        userId,
        role);
  }

  private UUID insertSession(UUID campaignId, UUID ownerId, int sequenceNumber, String status) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO sessions (id, campaign_id, owner_id, status, sequence_number, created_at,"
            + " updated_at) VALUES (?, ?, ?, ?, ?, now(), now())",
        id,
        campaignId,
        ownerId,
        status,
        sequenceNumber);
    return id;
  }

  private UUID insertEntity(
      String headTable, UUID campaignId, UUID ownerId, UUID sessionId, String name) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO "
            + headTable
            + " (id, campaign_id, owner_id, name, created_at, created_in_session_id)"
            + " VALUES (?, ?, ?, ?, now(), ?)",
        id,
        campaignId,
        ownerId,
        name,
        sessionId);
    return id;
  }

  private void insertVersion(
      String versionTable,
      String fkColumn,
      UUID entityId,
      UUID sessionId,
      int versionNumber,
      String changedFieldsJson,
      String snapshot) {
    jdbcTemplate.update(
        "INSERT INTO "
            + versionTable
            + " (id, "
            + fkColumn
            + ", session_id, version_number, changed_fields, full_snapshot, created_at)"
            + " VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, now())",
        UUID.randomUUID(),
        entityId,
        sessionId,
        versionNumber,
        changedFieldsJson,
        snapshot);
  }

  private void insertAnnotation(
      UUID campaignId, String entityType, UUID entityId, UUID authorId, String content) {
    jdbcTemplate.update(
        "INSERT INTO annotations (id, campaign_id, entity_type, entity_id, author_id, content,"
            + " created_at) VALUES (?, ?, ?, ?, ?, ?, now())",
        UUID.randomUUID(),
        campaignId,
        entityType,
        entityId,
        authorId,
        content);
  }
}
