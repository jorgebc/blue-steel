package com.bluesteel.application.model.campaign;

import java.time.Instant;
import java.util.List;

/**
 * Top-level campaign export archive (D-112): the full campaign assembled for a portable, raw-JSON
 * download — metadata, members, the four world-state entity types each with their complete
 * append-only version history, annotations, and sessions. Provider-neutral value record with no
 * Spring/JPA/web imports (ARCH-01/ARCH-07).
 */
public record CampaignArchive(
    String schemaVersion,
    Instant exportedAt,
    ArchivedCampaign campaign,
    List<ArchivedMember> members,
    List<ArchivedEntity> entities,
    List<ArchivedAnnotation> annotations,
    List<ArchivedSession> sessions) {

  public CampaignArchive {
    members = members == null ? List.of() : members;
    entities = entities == null ? List.of() : entities;
    annotations = annotations == null ? List.of() : annotations;
    sessions = sessions == null ? List.of() : sessions;
  }
}
