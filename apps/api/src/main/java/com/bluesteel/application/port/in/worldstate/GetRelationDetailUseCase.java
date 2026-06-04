package com.bluesteel.application.port.in.worldstate;

import com.bluesteel.application.model.worldstate.RelationDetailView;
import java.util.UUID;

/**
 * Driving port for reading a single relation with its endpoints and full version history for any
 * campaign member (F4.3.6, D-030).
 */
public interface GetRelationDetailUseCase {

  /** Returns the relation detail, or throws when the relation is absent from the campaign. */
  RelationDetailView getDetail(UUID campaignId, UUID relationId, UUID callerId);
}
