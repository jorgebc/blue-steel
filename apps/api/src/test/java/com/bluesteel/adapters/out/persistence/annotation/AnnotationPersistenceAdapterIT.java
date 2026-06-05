package com.bluesteel.adapters.out.persistence.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import com.bluesteel.TestcontainersPostgresBaseIT;
import com.bluesteel.domain.annotation.Annotation;
import com.bluesteel.domain.annotation.AnnotationEntityType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@DisplayName("AnnotationPersistenceAdapter")
@Transactional
class AnnotationPersistenceAdapterIT extends TestcontainersPostgresBaseIT {

  @Autowired private AnnotationPersistenceAdapter adapter;
  @Autowired private JdbcTemplate jdbcTemplate;

  private static final UUID CAMPAIGN_ID = UUID.randomUUID();
  private static final UUID ENTITY_ID = UUID.randomUUID();

  @Test
  @DisplayName("should save and find an annotation by id")
  void save_thenFindById_returnsAnnotation() {
    UUID userId = insertUser();
    insertCampaignMember(userId);
    Annotation saved = adapter.save(annotation(userId, "actor", ENTITY_ID));

    Optional<Annotation> found = adapter.findById(saved.id());

    assertThat(found).isPresent();
    assertThat(found.get().id()).isEqualTo(saved.id());
    assertThat(found.get().content()).isEqualTo("A note");
    assertThat(found.get().entityType()).isEqualTo(AnnotationEntityType.actor);
    assertThat(found.get().authorId()).isEqualTo(userId);
  }

  @Test
  @DisplayName("should return empty when annotation id does not exist")
  void findById_missing_returnsEmpty() {
    assertThat(adapter.findById(UUID.randomUUID())).isEmpty();
  }

  @Test
  @DisplayName("should list annotations by entity_type, entity_id, and campaign_id")
  void findByEntityTypeAndEntityIdAndCampaignId_returnsMatchingAnnotations() {
    UUID userId = insertUser();
    insertCampaignMember(userId);
    UUID otherEntityId = UUID.randomUUID();

    adapter.save(annotation(userId, "actor", ENTITY_ID));
    adapter.save(annotation(userId, "actor", ENTITY_ID));
    adapter.save(annotation(userId, "space", ENTITY_ID));
    adapter.save(annotation(userId, "actor", otherEntityId));

    List<Annotation> results =
        adapter.findByEntityTypeAndEntityIdAndCampaignId("actor", ENTITY_ID, CAMPAIGN_ID);

    assertThat(results).hasSize(2);
    assertThat(results).allMatch(a -> a.entityType() == AnnotationEntityType.actor);
    assertThat(results).allMatch(a -> a.entityId().equals(ENTITY_ID));
  }

  @Test
  @DisplayName("should delete an annotation by id")
  void deleteById_removesAnnotation() {
    UUID userId = insertUser();
    insertCampaignMember(userId);
    Annotation saved = adapter.save(annotation(userId, "relation", ENTITY_ID));

    adapter.deleteById(saved.id());

    assertThat(adapter.findById(saved.id())).isEmpty();
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private Annotation annotation(UUID authorId, String entityType, UUID entityId) {
    return Annotation.create(
        UUID.randomUUID(),
        CAMPAIGN_ID,
        entityId,
        AnnotationEntityType.valueOf(entityType),
        authorId,
        "A note",
        Instant.now().truncatedTo(ChronoUnit.MICROS));
  }

  private UUID insertUser() {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO users (id, email, password_hash, is_admin, force_password_change, created_at)"
            + " VALUES (?, ?, 'hash', FALSE, FALSE, now())",
        id,
        id + "@test.com");
    return id;
  }

  private void insertCampaignMember(UUID userId) {
    jdbcTemplate.update(
        "INSERT INTO campaigns (id, name, created_by, created_at) VALUES (?, 'Test', ?, now())"
            + " ON CONFLICT DO NOTHING",
        CAMPAIGN_ID,
        userId);
  }
}
