package com.bluesteel.application.service.proposal;

import com.bluesteel.application.model.worldstate.EntityDetailView;
import com.bluesteel.application.model.worldstate.EntityVersionView;
import com.bluesteel.application.model.worldstate.EntityWriteCommand;
import com.bluesteel.application.port.out.worldstate.WorldStateReadPort;
import com.bluesteel.domain.exception.ProposalTargetNotFoundException;
import com.bluesteel.domain.proposal.Proposal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Translates a proposal's effective delta into an {@link EntityWriteCommand} for the existing
 * world-state write path (actor/space targets only, D-108). Reads the target's current head and
 * latest {@code full_snapshot} via {@link WorldStateReadPort}, merges the delta over it, and stamps
 * the command with the campaign's latest committed session id rather than the proposal's provenance
 * session (D-107, D-110).
 */
@Component
public class ProposalDeltaMapper {

  private final WorldStateReadPort worldStateReadPort;

  public ProposalDeltaMapper(WorldStateReadPort worldStateReadPort) {
    this.worldStateReadPort = worldStateReadPort;
  }

  /**
   * Builds the write command appending a new version to the target entity. {@code effectiveDelta}
   * is the GM-edited delta when present, otherwise the author's delta (resolved by the caller);
   * {@code latestCommittedSessionId} is the session the new version is attributed to (D-107).
   */
  public EntityWriteCommand toWriteCommand(
      Proposal proposal, Map<String, Object> effectiveDelta, UUID latestCommittedSessionId) {
    String entityType = proposal.targetType().name().toLowerCase(Locale.ROOT);

    EntityDetailView head =
        worldStateReadPort.getWithHistory(entityType, proposal.campaignId(), proposal.targetId());
    if (head == null) {
      throw new ProposalTargetNotFoundException(
          "Target "
              + entityType
              + " "
              + proposal.targetId()
              + " no longer exists in this campaign");
    }

    Map<String, Object> merged = new HashMap<>(currentSnapshot(head));
    merged.putAll(effectiveDelta);

    String name = effectiveDelta.get("name") instanceof String n && !n.isBlank() ? n : head.name();

    return new EntityWriteCommand(
        entityType,
        proposal.targetId(),
        proposal.campaignId(),
        head.ownerId(),
        name,
        effectiveDelta,
        merged,
        latestCommittedSessionId);
  }

  private static Map<String, Object> currentSnapshot(EntityDetailView head) {
    List<EntityVersionView> versions = head.versions();
    if (versions == null || versions.isEmpty()) {
      return Map.of();
    }
    Map<String, Object> snapshot = versions.get(versions.size() - 1).fullSnapshot();
    return snapshot == null ? Map.of() : snapshot;
  }
}
