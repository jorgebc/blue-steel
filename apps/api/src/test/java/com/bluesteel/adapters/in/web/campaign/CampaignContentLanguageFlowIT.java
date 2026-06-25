package com.bluesteel.adapters.in.web.campaign;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bluesteel.BlueSteelApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * End-to-end seam IT for the per-campaign content language (F9, D-099/D-103): exercises the path
 * the isolated slices cannot cover together. The {@code WebMvcTest} mocks the service, the
 * persistence IT never goes through HTTP, and the pipeline services are unit-tested with mocked
 * ports — none of them prove that a language <em>persisted in the DB</em> is loaded back and
 * forwarded to the LLM adapter through real Spring wiring. The {@code local} profile wires the
 * deterministic mock LLM adapters, which echo the campaign language (Spanish for {@code es}), so
 * the assertions are stable without a live provider.
 */
@ActiveProfiles("local")
@SpringBootTest(
    classes = BlueSteelApplication.class,
    webEnvironment = WebEnvironment.MOCK,
    properties = {
      "spring.jpa.hibernate.ddl-auto=none",
      "admin.email=bootstrap-test@example.com",
      "admin.password=Bootstrap!Test123456",
      "jwt.secret=test-jwt-secret-test-jwt-secret-test-jwt-secret!"
    })
@Transactional
@DisplayName("Content-language flow (create → read-back → query) over real HTTP + DB")
class CampaignContentLanguageFlowIT {

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
  private static final UUID MEMBER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c3");
  private static final UUID ES_CAMPAIGN_ID =
      UUID.fromString("11111111-1111-1111-1111-1111111111e5");
  private static final UUID EN_CAMPAIGN_ID =
      UUID.fromString("11111111-1111-1111-1111-1111111111e0");

  @BeforeEach
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  @DisplayName("should persist contentLanguage=es on create and return it on a later GET read-back")
  @WithMockUser(username = "00000000-0000-0000-0000-0000000000a1", roles = "ADMIN")
  void create_persistsContentLanguage_andReadsItBackOverHttp() throws Exception {
    insertUser(ADMIN_ID);
    insertUser(GM_ID);

    String created =
        mockMvc
            .perform(
                post("/api/v1/campaigns")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        { "name": "Maldición de Strahd",
                          "gmUserId": "%s",
                          "contentLanguage": "es" }
                        """
                            .formatted(GM_ID)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.contentLanguage").value("es"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    UUID campaignId = UUID.fromString(objectMapper.readTree(created).at("/data/id").asText());

    mockMvc
        .perform(get("/api/v1/campaigns/{id}", campaignId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(campaignId.toString()))
        .andExpect(jsonPath("$.data.contentLanguage").value("es"));
  }

  @Test
  @DisplayName(
      "should answer a query in Spanish when the persisted campaign language is es (D-103)")
  @WithMockUser(username = "00000000-0000-0000-0000-0000000000c3", roles = "USER")
  void query_esCampaign_answersInSpanish() throws Exception {
    insertUser(MEMBER_ID);
    insertCampaign(ES_CAMPAIGN_ID, MEMBER_ID, "es");
    insertMember(ES_CAMPAIGN_ID, MEMBER_ID, "gm");

    mockMvc
        .perform(
            post("/api/v1/campaigns/{id}/queries", ES_CAMPAIGN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"question\": \"¿Quién es Mira?\" }"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.data.answer").value("Esta es una respuesta simulada a: ¿Quién es Mira?"));
  }

  @Test
  @DisplayName(
      "should answer the same query in English for an en campaign — per-campaign isolation")
  @WithMockUser(username = "00000000-0000-0000-0000-0000000000c3", roles = "USER")
  void query_enCampaign_answersInEnglish() throws Exception {
    insertUser(MEMBER_ID);
    insertCampaign(EN_CAMPAIGN_ID, MEMBER_ID, "en");
    insertMember(EN_CAMPAIGN_ID, MEMBER_ID, "gm");

    mockMvc
        .perform(
            post("/api/v1/campaigns/{id}/queries", EN_CAMPAIGN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"question\": \"Who is Mira?\" }"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.answer").value("This is a mock answer to: Who is Mira?"));
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

  private void insertCampaign(UUID id, UUID createdBy, String contentLanguage) {
    jdbcTemplate.update(
        "INSERT INTO campaigns (id, name, created_by, content_language, created_at)"
            + " VALUES (?, 'Test', ?, ?, now())",
        id,
        createdBy,
        contentLanguage);
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
}
