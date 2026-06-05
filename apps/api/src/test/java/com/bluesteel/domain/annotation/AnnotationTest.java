package com.bluesteel.domain.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bluesteel.domain.exception.DomainException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Annotation domain entity")
class AnnotationTest {

  private static final UUID ID = UUID.randomUUID();
  private static final UUID CAMPAIGN_ID = UUID.randomUUID();
  private static final UUID ENTITY_ID = UUID.randomUUID();
  private static final UUID AUTHOR_ID = UUID.randomUUID();
  private static final Instant NOW = Instant.now();

  @Test
  @DisplayName("should create annotation with valid fields")
  void create_validFields_succeeds() {
    Annotation annotation =
        Annotation.create(
            ID, CAMPAIGN_ID, ENTITY_ID, AnnotationEntityType.actor, AUTHOR_ID, "Hello", NOW);

    assertThat(annotation.id()).isEqualTo(ID);
    assertThat(annotation.campaignId()).isEqualTo(CAMPAIGN_ID);
    assertThat(annotation.entityId()).isEqualTo(ENTITY_ID);
    assertThat(annotation.entityType()).isEqualTo(AnnotationEntityType.actor);
    assertThat(annotation.authorId()).isEqualTo(AUTHOR_ID);
    assertThat(annotation.content()).isEqualTo("Hello");
    assertThat(annotation.createdAt()).isEqualTo(NOW);
  }

  @Nested
  @DisplayName("content validation")
  class ContentValidation {

    @Test
    @DisplayName("should throw DomainException when content is blank")
    void create_blankContent_throwsDomainException() {
      assertThrows(
          DomainException.class,
          () ->
              Annotation.create(
                  ID, CAMPAIGN_ID, ENTITY_ID, AnnotationEntityType.actor, AUTHOR_ID, "  ", NOW));
    }

    @Test
    @DisplayName("should throw DomainException when content is empty string")
    void create_emptyContent_throwsDomainException() {
      assertThrows(
          DomainException.class,
          () ->
              Annotation.create(
                  ID, CAMPAIGN_ID, ENTITY_ID, AnnotationEntityType.actor, AUTHOR_ID, "", NOW));
    }

    @Test
    @DisplayName("should throw DomainException when content is null")
    void create_nullContent_throwsDomainException() {
      assertThrows(
          DomainException.class,
          () ->
              Annotation.create(
                  ID, CAMPAIGN_ID, ENTITY_ID, AnnotationEntityType.actor, AUTHOR_ID, null, NOW));
    }
  }

  @Nested
  @DisplayName("entityType validation")
  class EntityTypeValidation {

    @Test
    @DisplayName("should throw DomainException when entityType is null")
    void create_nullEntityType_throwsDomainException() {
      assertThrows(
          DomainException.class,
          () -> Annotation.create(ID, CAMPAIGN_ID, ENTITY_ID, null, AUTHOR_ID, "Some note", NOW));
    }

    @Test
    @DisplayName("should accept all valid entity types")
    void create_allValidEntityTypes_succeed() {
      for (AnnotationEntityType type : AnnotationEntityType.values()) {
        Annotation annotation =
            Annotation.create(
                UUID.randomUUID(), CAMPAIGN_ID, ENTITY_ID, type, AUTHOR_ID, "Note", NOW);
        assertThat(annotation.entityType()).isEqualTo(type);
      }
    }
  }
}
