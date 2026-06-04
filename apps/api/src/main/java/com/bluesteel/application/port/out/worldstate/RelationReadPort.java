package com.bluesteel.application.port.out.worldstate;

import com.bluesteel.application.model.worldstate.RelationDetailView;
import com.bluesteel.application.model.worldstate.RelationSummaryView;
import java.util.List;
import java.util.UUID;

/**
 * Driven port for read-only relation queries exposing the structured graph endpoints (F4.3.5,
 * D-030, D-089). Reads are scoped to a campaign.
 */
public interface RelationReadPort {

  /**
   * Returns all relations in the campaign with their latest version. When {@code actorFilter} is
   * non-null, only relations whose source or target endpoint is that entity are returned.
   */
  List<RelationSummaryView> list(UUID campaignId, UUID actorFilter);

  /**
   * Returns the relation's identity, endpoints, and full ordered version history, or {@code null}
   * if no such relation exists in the campaign.
   */
  RelationDetailView getWithHistory(UUID campaignId, UUID relationId);
}
