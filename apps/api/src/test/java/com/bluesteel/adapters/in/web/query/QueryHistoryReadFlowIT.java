package com.bluesteel.adapters.in.web.query;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bluesteel.BlueSteelApplication;
import com.bluesteel.application.model.query.Citation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
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
 * End-to-end read-path IT for the Q&A log: seeds entries against a live Postgres, then reads them
 * back through the HTTP endpoint with a real service + membership authorization. Covers the seam
 * the mocked {@code QueryControllerTest} and the isolated adapter ITs cannot exercise together —
 * controller JSON serialization over a real DB read with real campaign-membership auth (F6.4,
 * D-058). Seeds the JSONB {@code citations} via the same Jackson shape the persistence adapter
 * writes, so the adapter's deserialization path is exercised on read.
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
@DisplayName("Q&A history read flow (controller → service → DB)")
class QueryHistoryReadFlowIT {

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

  private static final UUID MEMBER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID CAMPAIGN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID OTHER_CAMPAIGN_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID SESSION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

  @BeforeEach
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  @DisplayName("should return a member's history newest-first with citations, meta, and no askerId")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void member_readsHistory_returns200() throws Exception {
    seedMemberCampaign();
    Instant base = Instant.parse("2026-06-17T10:00:00Z");
    Citation citation = new Citation(SESSION_ID, 4, "Mira joined the party.");
    insertQueryLog("Who is Mira?", "Mira is a rogue.", List.of(citation), base);
    insertQueryLog("Where did Aldric go?", "Aldric fled north.", List.of(), base.plusSeconds(60));

    mockMvc
        .perform(get("/api/v1/campaigns/{id}/queries/history", CAMPAIGN_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].question").value("Where did Aldric go?"))
        .andExpect(jsonPath("$.data[1].question").value("Who is Mira?"))
        .andExpect(jsonPath("$.data[1].answer").value("Mira is a rogue."))
        .andExpect(jsonPath("$.data[1].citations[0].sessionId").value(SESSION_ID.toString()))
        .andExpect(jsonPath("$.data[1].citations[0].sequenceNumber").value(4))
        .andExpect(jsonPath("$.data[1].citations[0].snippet").value("Mira joined the party."))
        .andExpect(jsonPath("$.data[1].createdAt").value(startsWith("2026-06-17T10:00:00")))
        .andExpect(jsonPath("$.data[0].askerId").doesNotExist())
        .andExpect(jsonPath("$.meta.page").value(0))
        .andExpect(jsonPath("$.meta.size").value(20))
        .andExpect(jsonPath("$.meta.totalCount").value(2))
        .andExpect(jsonPath("$.errors").isEmpty());
  }

  @Test
  @DisplayName("should honour offset paging, returning the oldest entry on the second page")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void member_pagesThroughHistory_returnsSecondPage() throws Exception {
    seedMemberCampaign();
    Instant base = Instant.parse("2026-06-17T10:00:00Z");
    insertQueryLog("q1", "a1", List.of(), base);
    insertQueryLog("q2", "a2", List.of(), base.plusSeconds(60));
    insertQueryLog("q3", "a3", List.of(), base.plusSeconds(120));

    mockMvc
        .perform(get("/api/v1/campaigns/{id}/queries/history?page=1&size=2", CAMPAIGN_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].question").value("q1"))
        .andExpect(jsonPath("$.meta.page").value(1))
        .andExpect(jsonPath("$.meta.size").value(2))
        .andExpect(jsonPath("$.meta.totalCount").value(3));
  }

  @Test
  @DisplayName("should never leak another campaign's entries into a member's history")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void member_readsHistory_isScopedToTheCampaign() throws Exception {
    seedMemberCampaign();
    insertCampaign(OTHER_CAMPAIGN_ID, MEMBER_ID);
    Instant base = Instant.parse("2026-06-17T10:00:00Z");
    insertQueryLog(CAMPAIGN_ID, "Mine question", "Mine answer", base);
    insertQueryLog(
        OTHER_CAMPAIGN_ID, "Other campaign question", "Other answer", base.plusSeconds(60));

    mockMvc
        .perform(get("/api/v1/campaigns/{id}/queries/history", CAMPAIGN_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].question").value("Mine question"))
        .andExpect(jsonPath("$.meta.totalCount").value(1));
  }

  @Test
  @DisplayName("should return 403 when a non-member requests the history")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void nonMember_readsHistory_returns403() throws Exception {
    insertUser(MEMBER_ID);
    insertCampaign(CAMPAIGN_ID, MEMBER_ID); // caller exists and campaign exists, but no membership

    mockMvc
        .perform(get("/api/v1/campaigns/{id}/queries/history", CAMPAIGN_ID))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errors[0].code").value("FORBIDDEN"));
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private void seedMemberCampaign() {
    insertUser(MEMBER_ID);
    insertCampaign(CAMPAIGN_ID, MEMBER_ID);
    insertMember(CAMPAIGN_ID, MEMBER_ID, "gm");
  }

  private void insertQueryLog(String question, String answer, List<Citation> citations, Instant at)
      throws JsonProcessingException {
    insertQueryLog(CAMPAIGN_ID, question, answer, citations, at);
  }

  private void insertQueryLog(UUID campaignId, String question, String answer, Instant at)
      throws JsonProcessingException {
    insertQueryLog(campaignId, question, answer, List.of(), at);
  }

  private void insertQueryLog(
      UUID campaignId, String question, String answer, List<Citation> citations, Instant at)
      throws JsonProcessingException {
    jdbcTemplate.update(
        "INSERT INTO query_log (id, campaign_id, asker_id, question, answer, citations, created_at)"
            + " VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)",
        UUID.randomUUID(),
        campaignId,
        MEMBER_ID,
        question,
        answer,
        objectMapper.writeValueAsString(citations),
        OffsetDateTime.ofInstant(at, ZoneOffset.UTC));
  }

  private void insertUser(UUID id) {
    jdbcTemplate.update(
        "INSERT INTO users (id, email, password_hash, is_admin, force_password_change, created_at)"
            + " VALUES (?, ?, 'hash', FALSE, FALSE, now())",
        id,
        id + "@test.com");
  }

  private void insertCampaign(UUID id, UUID createdBy) {
    jdbcTemplate.update(
        "INSERT INTO campaigns (id, name, created_by, created_at) VALUES (?, 'Test', ?, now())",
        id,
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
}
