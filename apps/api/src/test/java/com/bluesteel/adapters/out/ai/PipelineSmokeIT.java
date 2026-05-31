package com.bluesteel.adapters.out.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

import com.bluesteel.BlueSteelApplication;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * End-to-end pipeline smoke test using mock LLM adapters (zero API cost, CI-safe).
 *
 * <p>Runs against a real PostgreSQL + pgvector Testcontainer under the {@code local} profile. Walks
 * the full session lifecycle: submit → poll → diff → commit, verifying that the full Spring wiring
 * is correct for any provider (adapters are provider-neutral — {@code @Profile("llm-real |
 * llm-ollama")}).
 *
 * <p>Uses {@code webEnvironment = RANDOM_PORT} because the test drives the real HTTP layer via
 * {@link TestRestTemplate}. Cannot extend {@code TestcontainersPostgresBaseIT} which is wired with
 * {@code NONE}.
 */
@ActiveProfiles("local")
@SpringBootTest(
    classes = BlueSteelApplication.class,
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.jpa.hibernate.ddl-auto=none",
      "admin.email=pipeline-smoke@example.com",
      "admin.password=PipelineSmoke!123456"
    })
@DisplayName("Pipeline smoke — full session lifecycle with mock LLM adapters")
class PipelineSmokeIT {

  static final PostgreSQLContainer<?> postgres;

  static {
    postgres =
        new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("bluesteel_smoke_test")
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

  @LocalServerPort private int port;

  /** RestTemplate that never throws on non-2xx responses — lets tests assert on the status. */
  private final RestTemplate restTemplate = noThrowRestTemplate();

  private String url(String path) {
    return "http://localhost:" + port + path;
  }

  private static RestTemplate noThrowRestTemplate() {
    RestTemplate rt = new RestTemplate();
    rt.setErrorHandler(
        new DefaultResponseErrorHandler() {
          @Override
          public boolean hasError(ClientHttpResponse response) {
            return false;
          }
        });
    return rt;
  }

  @Test
  @DisplayName(
      "should complete the full session lifecycle: submit → draft → commit using mock adapters")
  @SuppressWarnings("unchecked")
  void fullSessionLifecycle_withMockAdapters_reachesDraftAndCommitsSuccessfully()
      throws InterruptedException {

    // ── 1. Login as bootstrap admin ──────────────────────────────────────────
    Map<String, String> loginBody =
        Map.of("email", "pipeline-smoke@example.com", "password", "PipelineSmoke!123456");
    ResponseEntity<Map> loginResponse =
        restTemplate.exchange(
            url("/api/v1/auth/login"), POST, new HttpEntity<>(loginBody, jsonHeaders()), Map.class);
    assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    String accessToken = (String) data(loginResponse).get("accessToken");
    assertThat(accessToken).isNotBlank();

    // ── 2. Get admin user ID ─────────────────────────────────────────────────
    ResponseEntity<Map> meResponse =
        restTemplate.exchange(
            url("/api/v1/users/me"), GET, new HttpEntity<>(authHeaders(accessToken)), Map.class);
    assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    String adminId = (String) data(meResponse).get("id");

    // ── 3. Create campaign (admin as GM) ─────────────────────────────────────
    Map<String, Object> campaignBody = Map.of("name", "Smoke Campaign", "gmUserId", adminId);
    ResponseEntity<Map> campaignResponse =
        restTemplate.exchange(
            url("/api/v1/campaigns"),
            POST,
            new HttpEntity<>(campaignBody, authHeaders(accessToken)),
            Map.class);
    assertThat(campaignResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String campaignId = (String) data(campaignResponse).get("id");

    // ── 4. Submit session ────────────────────────────────────────────────────
    Map<String, Object> sessionBody =
        Map.of("summaryText", "The party met Mira and Aldric near the ruins of Thornwick.");
    ResponseEntity<Map> sessionResponse =
        restTemplate.exchange(
            url("/api/v1/campaigns/" + campaignId + "/sessions"),
            POST,
            new HttpEntity<>(sessionBody, authHeaders(accessToken)),
            Map.class);
    assertThat(sessionResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    String sessionId = (String) data(sessionResponse).get("sessionId");

    // ── 5. Poll for DRAFT status (mock pipeline completes in milliseconds) ───
    String status = pollUntilDraftOrFailed(campaignId, sessionId, accessToken, 30);
    assertThat(status)
        .as("session should reach draft status — check ingestion pipeline for errors")
        .isEqualTo("DRAFT");

    // ── 6. Retrieve diff ─────────────────────────────────────────────────────
    ResponseEntity<Map> diffResponse =
        restTemplate.exchange(
            url("/api/v1/campaigns/" + campaignId + "/sessions/" + sessionId + "/diff"),
            GET,
            new HttpEntity<>(authHeaders(accessToken)),
            Map.class);
    assertThat(diffResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    Map<String, Object> diffData = data(diffResponse);
    assertThat(diffData).containsKey("actors");

    // ── 7. Build commit payload from diff ────────────────────────────────────
    // ExistingEntityCard (Mira → MATCH in fresh DB) uses "delete" to skip world-state write
    // and avoid FK violation (actor entity doesn't exist in empty DB). All other cards: "accept".
    List<Map<String, Object>> cardDecisions = buildCardDecisions(diffData);
    List<Map<String, Object>> uncertainResolutions = buildUncertainResolutions(diffData);
    List<Map<String, Object>> acknowledgedConflicts = buildAcknowledgedConflicts(diffData);

    assertThat(cardDecisions).isNotEmpty();

    Map<String, Object> commitBody = new HashMap<>();
    commitBody.put("cardDecisions", cardDecisions);
    commitBody.put("uncertainResolutions", uncertainResolutions);
    commitBody.put("acknowledgedConflicts", acknowledgedConflicts);

    // ── 8. Commit ────────────────────────────────────────────────────────────
    ResponseEntity<Map> commitResponse =
        restTemplate.exchange(
            url("/api/v1/campaigns/" + campaignId + "/sessions/" + sessionId + "/commit"),
            POST,
            new HttpEntity<>(commitBody, authHeaders(accessToken)),
            Map.class);
    assertThat(commitResponse.getStatusCode())
        .as("commit should return 200 — errors: " + commitResponse.getBody())
        .isEqualTo(HttpStatus.OK);
  }

  // ── Helpers ─────────────────────────────────────────────────────────────────

  @SuppressWarnings("unchecked")
  private String pollUntilDraftOrFailed(
      String campaignId, String sessionId, String token, int maxAttempts)
      throws InterruptedException {
    for (int i = 0; i < maxAttempts; i++) {
      ResponseEntity<Map> statusResponse =
          restTemplate.exchange(
              url("/api/v1/campaigns/" + campaignId + "/sessions/" + sessionId + "/status"),
              GET,
              new HttpEntity<>(authHeaders(token)),
              Map.class);
      String status = (String) data(statusResponse).get("status");
      if ("DRAFT".equals(status) || "FAILED".equals(status)) {
        return status;
      }
      Thread.sleep(1_000);
    }
    return "timeout";
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> buildCardDecisions(Map<String, Object> diffData) {
    List<Map<String, Object>> decisions = new ArrayList<>();
    for (String category : List.of("actors", "spaces", "events", "relations")) {
      List<Map<String, Object>> cards = (List<Map<String, Object>>) diffData.get(category);
      if (cards == null) continue;
      for (Map<String, Object> card : cards) {
        String cardType = (String) card.get("cardType");
        if ("UNCERTAIN".equals(cardType)) continue; // handled via uncertainResolutions
        // ExistingEntityCard references an entity that may not exist in the fresh test DB.
        // Use "delete" to skip the world-state write and avoid FK violation.
        String action = "EXISTING".equals(cardType) ? "delete" : "accept";
        decisions.add(Map.of("cardId", card.get("cardId"), "action", action));
      }
    }
    return decisions;
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> buildUncertainResolutions(Map<String, Object> diffData) {
    List<Map<String, Object>> resolutions = new ArrayList<>();
    for (String category : List.of("actors", "spaces", "events", "relations")) {
      List<Map<String, Object>> cards = (List<Map<String, Object>>) diffData.get(category);
      if (cards == null) continue;
      for (Map<String, Object> card : cards) {
        if ("UNCERTAIN".equals(card.get("cardType"))) {
          resolutions.add(Map.of("cardId", card.get("cardId"), "resolution", "NEW"));
        }
      }
    }
    return resolutions;
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> buildAcknowledgedConflicts(Map<String, Object> diffData) {
    List<Map<String, Object>> acknowledged = new ArrayList<>();
    List<Map<String, Object>> conflicts =
        (List<Map<String, Object>>) diffData.get("detectedConflicts");
    if (conflicts == null) return acknowledged;
    for (Map<String, Object> conflict : conflicts) {
      acknowledged.add(Map.of("conflictId", conflict.get("conflictId")));
    }
    return acknowledged;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> data(ResponseEntity<Map> response) {
    return (Map<String, Object>) response.getBody().get("data");
  }

  private static HttpHeaders authHeaders(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private static HttpHeaders jsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }
}
