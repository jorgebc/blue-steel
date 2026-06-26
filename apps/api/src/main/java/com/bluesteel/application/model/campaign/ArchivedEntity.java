package com.bluesteel.application.model.campaign;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A world-state entity (actor/space/event/relation, tagged by {@code type}) with its complete
 * ordered version history, as carried in a {@link CampaignArchive} export (D-112).
 */
public record ArchivedEntity(
    String type,
    UUID id,
    String name,
    UUID ownerId,
    Instant createdAt,
    List<ArchivedEntityVersion> versions) {

  public ArchivedEntity {
    versions = versions == null ? List.of() : versions;
  }
}
