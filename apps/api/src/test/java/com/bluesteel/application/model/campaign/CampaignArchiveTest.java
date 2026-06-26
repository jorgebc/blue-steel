package com.bluesteel.application.model.campaign;

import static org.assertj.core.api.Assertions.assertThat;

import com.bluesteel.domain.campaign.CampaignRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CampaignArchive")
class CampaignArchiveTest {

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @Test
  @DisplayName("should default null section lists to empty, never null")
  void compactConstructor_defaultsNullListsToEmpty() {
    CampaignArchive archive =
        new CampaignArchive(
            "1",
            Instant.now(),
            new ArchivedCampaign(
                UUID.randomUUID(), "Lost Mines", UUID.randomUUID(), Instant.now(), "en"),
            null,
            null,
            null,
            null);

    assertThat(archive.members()).isEmpty();
    assertThat(archive.entities()).isEmpty();
    assertThat(archive.annotations()).isEmpty();
    assertThat(archive.sessions()).isEmpty();
  }

  @Test
  @DisplayName("should default null entity versions to empty, never null")
  void archivedEntity_defaultsNullVersionsToEmpty() {
    ArchivedEntity entity =
        new ArchivedEntity(
            "actor", UUID.randomUUID(), "Gandalf", UUID.randomUUID(), Instant.now(), null);

    assertThat(entity.versions()).isEmpty();
  }

  @Test
  @DisplayName("should serialize the full archive tree with camelCase keys")
  void serialize_camelCaseTree() throws Exception {
    UUID entityId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();
    CampaignArchive archive =
        new CampaignArchive(
            "1",
            Instant.parse("2026-06-26T00:00:00Z"),
            new ArchivedCampaign(
                UUID.randomUUID(), "Lost Mines", UUID.randomUUID(), Instant.now(), "en"),
            List.of(new ArchivedMember(UUID.randomUUID(), CampaignRole.GM, Instant.now())),
            List.of(
                new ArchivedEntity(
                    "actor",
                    entityId,
                    "Gandalf",
                    UUID.randomUUID(),
                    Instant.now(),
                    List.of(
                        new ArchivedEntityVersion(
                            UUID.randomUUID(),
                            1,
                            sessionId,
                            Map.of("name", "Gandalf"),
                            Map.of("name", "Gandalf", "description", "A grey wizard"),
                            Instant.now())))),
            List.of(
                new ArchivedAnnotation(
                    UUID.randomUUID(),
                    "actor",
                    entityId,
                    UUID.randomUUID(),
                    "A note",
                    Instant.now())),
            List.of(
                new ArchivedSession(
                    sessionId, UUID.randomUUID(), 1, "committed", Instant.now(), Instant.now())));

    String json = objectMapper.writeValueAsString(archive);

    assertThat(json).contains("\"schemaVersion\":\"1\"");
    assertThat(json).contains("\"contentLanguage\":\"en\"");
    assertThat(json).contains("\"fullSnapshot\"");
    assertThat(json).contains("\"versionNumber\":1");
    assertThat(json).contains("\"sequenceNumber\":1");
    assertThat(json).contains("\"role\":\"GM\"");
  }
}
