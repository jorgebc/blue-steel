package com.bluesteel.application.port.out.worldstate;

import com.bluesteel.application.model.worldstate.CommittedEntityVersion;
import com.bluesteel.application.model.worldstate.EntityWriteCommand;
import com.bluesteel.application.model.worldstate.ResolvedEndpoint;
import java.util.Optional;
import java.util.UUID;

/** Driven port for writing world-state entity heads and version history (D-001, D-089). */
public interface WorldStatePort {

  /**
   * Creates a new entity head + version 1 when {@code cmd.existingEntityId()} is null; otherwise
   * appends the next version to the existing head.
   */
  CommittedEntityVersion writeEntity(EntityWriteCommand cmd);

  /**
   * Returns {@code true} if an entity of the given type with the given id belongs to the campaign.
   * Backs the D-079 {@code INVALID_ENTITY_REFERENCE} check at the application tier.
   */
  boolean existsInCampaign(String entityType, UUID entityId, UUID campaignId);

  /**
   * Best-effort resolution of a relation endpoint name to a committed actor or space in the
   * campaign (F4.3.4, D-095). Actors are searched before spaces; the first case-insensitive name
   * match wins. Returns empty when no actor or space matches.
   */
  Optional<ResolvedEndpoint> findEndpointByName(UUID campaignId, String name);

  /**
   * Best-effort resolution of a name to a committed entity id of one specific type ({@code "actor"}
   * or {@code "space"}) within the campaign (F4.6.4, D-095). The first case-insensitive name match
   * wins. Returns empty when no entity of that type matches. Used for event space/actor links,
   * where the target type is known and must not fall back to the other type.
   */
  Optional<UUID> findEntityIdByName(UUID campaignId, String name, String entityType);
}
