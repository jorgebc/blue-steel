package com.bluesteel.application.port.out.campaign;

import com.bluesteel.application.model.campaign.ArchivedAnnotation;
import com.bluesteel.application.model.campaign.ArchivedEntity;
import com.bluesteel.application.model.campaign.ArchivedSession;
import java.util.List;
import java.util.UUID;

/**
 * Bulk-read contract for assembling a campaign export archive (D-112). Reads are set-based and
 * bounded (no N+1; explicit fetch size) so the one "whole campaign at once" path stays within the
 * Render free-tier memory budget. Campaign metadata and members are read via the existing {@link
 * CampaignRepository} / {@link CampaignMembershipRepository}.
 */
public interface CampaignExportReadPort {

  /**
   * Cheap precheck (no row materialization) of how many world-state entities the campaign holds,
   * used to fail fast on oversized campaigns before any bulk read (D-112 mitigation 1).
   */
  long countEntities(UUID campaignId);

  /** All actors/spaces/events/relations of the campaign, each with full ordered version history. */
  List<ArchivedEntity> readEntities(UUID campaignId);

  /** All annotations of the campaign, oldest first. */
  List<ArchivedAnnotation> readAnnotations(UUID campaignId);

  /** All sessions of the campaign, oldest first. */
  List<ArchivedSession> readSessions(UUID campaignId);
}
