package com.bluesteel.application.service.campaign;

import com.bluesteel.application.model.campaign.ArchivedCampaign;
import com.bluesteel.application.model.campaign.ArchivedMember;
import com.bluesteel.application.model.campaign.CampaignArchive;
import com.bluesteel.application.port.in.campaign.ExportCampaignUseCase;
import com.bluesteel.application.port.out.campaign.CampaignExportReadPort;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.campaign.CampaignMembershipRepository;
import com.bluesteel.application.port.out.campaign.CampaignRepository;
import com.bluesteel.domain.campaign.Campaign;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.CampaignNotFoundException;
import com.bluesteel.domain.exception.ExportTooLargeException;
import com.bluesteel.domain.exception.UnauthorizedException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Authorizes (GM or admin, D-043) and assembles a {@link CampaignArchive} (D-112). Order matters
 * for memory: authorize → cheap {@code countEntities} cap precheck → only then load the bounded
 * archive, so an oversized campaign is rejected before any rows are materialized.
 */
@Service
public class ExportCampaignService implements ExportCampaignUseCase {

  /** Export schema version stamped into each archive; bump on any breaking shape change. */
  private static final String SCHEMA_VERSION = "1";

  private static final Logger log = LoggerFactory.getLogger(ExportCampaignService.class);

  private final CampaignRepository campaignRepository;
  private final CampaignMembershipRepository membershipRepository;
  private final CampaignMembershipPort membershipPort;
  private final CampaignExportReadPort exportReadPort;
  private final Clock clock;
  private final int maxEntities;

  public ExportCampaignService(
      CampaignRepository campaignRepository,
      CampaignMembershipRepository membershipRepository,
      CampaignMembershipPort membershipPort,
      CampaignExportReadPort exportReadPort,
      Clock clock,
      @Value("${campaign.export.max-entities:2000}") int maxEntities) {
    this.campaignRepository = campaignRepository;
    this.membershipRepository = membershipRepository;
    this.membershipPort = membershipPort;
    this.exportReadPort = exportReadPort;
    this.clock = clock;
    this.maxEntities = maxEntities;
  }

  @Override
  public CampaignArchive export(UUID campaignId, UUID callerId, boolean callerIsAdmin) {
    Campaign campaign =
        campaignRepository
            .findById(campaignId)
            .orElseThrow(() -> new CampaignNotFoundException(campaignId));

    authorize(campaignId, callerId, callerIsAdmin);

    long entityCount = exportReadPort.countEntities(campaignId);
    if (entityCount > maxEntities) {
      throw new ExportTooLargeException(
          "Campaign has "
              + entityCount
              + " entities, exceeding the export limit of "
              + maxEntities);
    }

    log.info(
        "Exporting campaign {} ({} entities) for caller {}", campaignId, entityCount, callerId);

    List<ArchivedMember> members =
        membershipRepository.findByCampaignId(campaignId).stream()
            .map(m -> new ArchivedMember(m.userId(), m.role(), m.joinedAt()))
            .toList();

    return new CampaignArchive(
        SCHEMA_VERSION,
        clock.instant(),
        new ArchivedCampaign(
            campaign.id(),
            campaign.name(),
            campaign.createdBy(),
            campaign.createdAt(),
            campaign.contentLanguage()),
        members,
        exportReadPort.readEntities(campaignId),
        exportReadPort.readAnnotations(campaignId),
        exportReadPort.readSessions(campaignId));
  }

  private void authorize(UUID campaignId, UUID callerId, boolean callerIsAdmin) {
    if (callerIsAdmin) {
      return;
    }
    Optional<CampaignRole> role = membershipPort.resolveRole(campaignId, callerId);
    if (role.orElse(null) != CampaignRole.GM) {
      throw new UnauthorizedException("Only the GM or an admin may export this campaign");
    }
  }
}
