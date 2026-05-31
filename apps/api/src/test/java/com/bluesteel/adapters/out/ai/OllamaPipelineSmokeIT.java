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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
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
 * Offline pipeline smoke test using real Ollama models — developer-run only.
 *
 * <p><b>Disabled by default.</b> Only runs when {@code RUN_OLLAMA_IT=true} is set in the
 * environment. CI has no Ollama instance so this test is always skipped there.
 *
 * <p><b>Pre-requisites (run before this test):</b>
 *
 * <ol>
 *   <li>Install Ollama: <a href="https://ollama.com">https://ollama.com</a>
 *   <li>{@code ollama pull bge-m3} (embeddings, 1024 dims)
 *   <li>{@code ollama pull qwen2.5:7b} (chat)
 *   <li>Recreate the local DB for 1024-dim vectors: {@code docker compose down -v && docker compose
 *       up -d} (D-088)
 * </ol>
 *
 * <p>Walks the same pipeline as {@link PipelineSmokeIT} but with real LLM calls, verifying the
 * Ollama beans wire correctly and the pipeline produces a committed session.
 */
@EnabledIfEnvironmentVariable(named = "RUN_OLLAMA_IT", matches = "true")
@ActiveProfiles({"local", "llm-ollama"})
@SpringBootTest(
    classes = BlueSteelApplication.class,
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.jpa.hibernate.ddl-auto=none",
      "admin.email=ollama-smoke@example.com",
      "admin.password=OllamaSmoke!123456"
    })
@DisplayName("Ollama pipeline smoke — full session lifecycle with real Ollama models [manual]")
class OllamaPipelineSmokeIT {

  static final PostgreSQLContainer<?> postgres;

  static {
    postgres =
        new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("bluesteel_ollama_smoke")
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
      "should complete the full session lifecycle with real Ollama models: submit → draft → commit")
  @SuppressWarnings("unchecked")
  void fullSessionLifecycle_withOllamaAdapters_reachesDraftAndCommitsSuccessfully()
      throws InterruptedException {

    // ── 1. Login ─────────────────────────────────────────────────────────────
    Map<String, String> loginBody =
        Map.of("email", "ollama-smoke@example.com", "password", "OllamaSmoke!123456");
    ResponseEntity<Map> loginResponse =
        restTemplate.exchange(
            url("/api/v1/auth/login"), POST, new HttpEntity<>(loginBody, jsonHeaders()), Map.class);
    assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    String accessToken = (String) data(loginResponse).get("accessToken");

    // ── 2. Get admin user ID ─────────────────────────────────────────────────
    ResponseEntity<Map> meResponse =
        restTemplate.exchange(
            url("/api/v1/users/me"), GET, new HttpEntity<>(authHeaders(accessToken)), Map.class);
    String adminId = (String) data(meResponse).get("id");

    // ── 3. Create campaign ────────────────────────────────────────────────────
    Map<String, Object> campaignBody = Map.of("name", "Ollama Smoke Campaign", "gmUserId", adminId);
    ResponseEntity<Map> campaignResponse =
        restTemplate.exchange(
            url("/api/v1/campaigns"),
            POST,
            new HttpEntity<>(campaignBody, authHeaders(accessToken)),
            Map.class);
    assertThat(campaignResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String campaignId = (String) data(campaignResponse).get("id");

    // ── 4. Submit session ─────────────────────────────────────────────────────
    Map<String, Object> sessionBody =
        Map.of(
            "summaryText",
            "The party arrived at the village of Oakhaven and met the elven ranger Sylara."
                + " She warned them of a dark cult gathering in the forest to the east."
                + " Aldric, the party's paladin, vowed to investigate.");
    ResponseEntity<Map> sessionResponse =
        restTemplate.exchange(
            url("/api/v1/campaigns/" + campaignId + "/sessions"),
            POST,
            new HttpEntity<>(sessionBody, authHeaders(accessToken)),
            Map.class);
    assertThat(sessionResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    String sessionId = (String) data(sessionResponse).get("sessionId");

    // ── 5. Poll for DRAFT (real Ollama — allow up to 120 s) ──────────────────
    String status = pollUntilDraftOrFailed(campaignId, sessionId, accessToken, 120);
    assertThat(status)
        .as("session should reach draft — verify Ollama is running and models are pulled")
        .isEqualTo("DRAFT");

    // ── 6. Retrieve diff ──────────────────────────────────────────────────────
    ResponseEntity<Map> diffResponse =
        restTemplate.exchange(
            url("/api/v1/campaigns/" + campaignId + "/sessions/" + sessionId + "/diff"),
            GET,
            new HttpEntity<>(authHeaders(accessToken)),
            Map.class);
    assertThat(diffResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    Map<String, Object> diffData = data(diffResponse);

    // ── 7. Build commit payload ───────────────────────────────────────────────
    List<Map<String, Object>> cardDecisions = buildCardDecisions(diffData);
    List<Map<String, Object>> uncertainResolutions = buildUncertainResolutions(diffData);
    List<Map<String, Object>> acknowledgedConflicts = buildAcknowledgedConflicts(diffData);

    Map<String, Object> commitBody = new HashMap<>();
    commitBody.put("cardDecisions", cardDecisions);
    commitBody.put("uncertainResolutions", uncertainResolutions);
    commitBody.put("acknowledgedConflicts", acknowledgedConflicts);

    // ── 8. Commit ─────────────────────────────────────────────────────────────
    ResponseEntity<Map> commitResponse =
        restTemplate.exchange(
            url("/api/v1/campaigns/" + campaignId + "/sessions/" + sessionId + "/commit"),
            POST,
            new HttpEntity<>(commitBody, authHeaders(accessToken)),
            Map.class);
    assertThat(commitResponse.getStatusCode())
        .as("commit errors: " + commitResponse.getBody())
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
        if ("UNCERTAIN".equals(cardType)) continue;
        // EXISTING cards reference entities that may not be in this fresh test DB; skip them.
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
