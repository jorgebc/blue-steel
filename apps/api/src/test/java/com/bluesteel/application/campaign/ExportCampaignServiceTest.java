package com.bluesteel.application.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluesteel.application.model.campaign.ArchivedAnnotation;
import com.bluesteel.application.model.campaign.ArchivedEntity;
import com.bluesteel.application.model.campaign.ArchivedSession;
import com.bluesteel.application.model.campaign.CampaignArchive;
import com.bluesteel.application.port.out.campaign.CampaignExportReadPort;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.campaign.CampaignMembershipRepository;
import com.bluesteel.application.port.out.campaign.CampaignRepository;
import com.bluesteel.application.service.campaign.ExportCampaignService;
import com.bluesteel.domain.campaign.Campaign;
import com.bluesteel.domain.campaign.CampaignMember;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.CampaignNotFoundException;
import com.bluesteel.domain.exception.ExportTooLargeException;
import com.bluesteel.domain.exception.UnauthorizedException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExportCampaignService")
class ExportCampaignServiceTest {

  private static final UUID CAMPAIGN_ID = UUID.randomUUID();
  private static final UUID CALLER_ID = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-06-26T12:00:00Z");
  private static final int MAX_ENTITIES = 2000;

  @Mock private CampaignRepository campaignRepository;
  @Mock private CampaignMembershipRepository membershipRepository;
  @Mock private CampaignMembershipPort membershipPort;
  @Mock private CampaignExportReadPort exportReadPort;

  private ExportCampaignService service;

  @BeforeEach
  void setUp() {
    service =
        new ExportCampaignService(
            campaignRepository,
            membershipRepository,
            membershipPort,
            exportReadPort,
            Clock.fixed(NOW, ZoneOffset.UTC),
            MAX_ENTITIES);
  }

  private Campaign campaign() {
    return Campaign.create(CAMPAIGN_ID, "Lost Mines", UUID.randomUUID(), Instant.now(), "es");
  }

  @Test
  @DisplayName("should assemble the archive when the caller is the GM")
  void export_gm_assemblesArchive() {
    UUID memberId = UUID.randomUUID();
    when(campaignRepository.findById(CAMPAIGN_ID)).thenReturn(Optional.of(campaign()));
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.GM));
    when(exportReadPort.countEntities(CAMPAIGN_ID)).thenReturn(3L);
    when(membershipRepository.findByCampaignId(CAMPAIGN_ID))
        .thenReturn(
            List.of(
                CampaignMember.create(
                    UUID.randomUUID(), CAMPAIGN_ID, memberId, CampaignRole.GM, NOW)));
    List<ArchivedEntity> entities =
        List.of(new ArchivedEntity("actor", UUID.randomUUID(), "Aldric", memberId, NOW, List.of()));
    List<ArchivedAnnotation> annotations =
        List.of(
            new ArchivedAnnotation(
                UUID.randomUUID(), "actor", UUID.randomUUID(), memberId, "note", NOW));
    List<ArchivedSession> sessions =
        List.of(new ArchivedSession(UUID.randomUUID(), memberId, 1, "committed", NOW, NOW));
    when(exportReadPort.readEntities(CAMPAIGN_ID)).thenReturn(entities);
    when(exportReadPort.readAnnotations(CAMPAIGN_ID)).thenReturn(annotations);
    when(exportReadPort.readSessions(CAMPAIGN_ID)).thenReturn(sessions);

    CampaignArchive archive = service.export(CAMPAIGN_ID, CALLER_ID, false);

    assertThat(archive.schemaVersion()).isEqualTo("1");
    assertThat(archive.exportedAt()).isEqualTo(NOW);
    assertThat(archive.campaign().id()).isEqualTo(CAMPAIGN_ID);
    assertThat(archive.campaign().contentLanguage()).isEqualTo("es");
    assertThat(archive.members())
        .singleElement()
        .satisfies(m -> assertThat(m.userId()).isEqualTo(memberId));
    assertThat(archive.entities()).isEqualTo(entities);
    assertThat(archive.annotations()).isEqualTo(annotations);
    assertThat(archive.sessions()).isEqualTo(sessions);
  }

  @Test
  @DisplayName("should assemble the archive for an admin who is not a campaign member")
  void export_admin_assemblesArchiveWithoutRoleResolution() {
    when(campaignRepository.findById(CAMPAIGN_ID)).thenReturn(Optional.of(campaign()));
    when(exportReadPort.countEntities(CAMPAIGN_ID)).thenReturn(0L);
    when(membershipRepository.findByCampaignId(CAMPAIGN_ID)).thenReturn(List.of());
    when(exportReadPort.readEntities(CAMPAIGN_ID)).thenReturn(List.of());
    when(exportReadPort.readAnnotations(CAMPAIGN_ID)).thenReturn(List.of());
    when(exportReadPort.readSessions(CAMPAIGN_ID)).thenReturn(List.of());

    CampaignArchive archive = service.export(CAMPAIGN_ID, CALLER_ID, true);

    assertThat(archive.campaign().id()).isEqualTo(CAMPAIGN_ID);
    verify(membershipPort, never()).resolveRole(CAMPAIGN_ID, CALLER_ID);
  }

  @Test
  @DisplayName("should reject an editor with UnauthorizedException before counting entities")
  void export_editor_throwsUnauthorized() {
    when(campaignRepository.findById(CAMPAIGN_ID)).thenReturn(Optional.of(campaign()));
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.EDITOR));

    assertThatThrownBy(() -> service.export(CAMPAIGN_ID, CALLER_ID, false))
        .isInstanceOf(UnauthorizedException.class);

    verify(exportReadPort, never()).countEntities(CAMPAIGN_ID);
  }

  @Test
  @DisplayName("should reject a non-member player with UnauthorizedException")
  void export_player_throwsUnauthorized() {
    when(campaignRepository.findById(CAMPAIGN_ID)).thenReturn(Optional.of(campaign()));
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.export(CAMPAIGN_ID, CALLER_ID, false))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  @DisplayName("should throw CampaignNotFoundException when the campaign does not exist")
  void export_unknownCampaign_throwsNotFound() {
    when(campaignRepository.findById(CAMPAIGN_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.export(CAMPAIGN_ID, CALLER_ID, true))
        .isInstanceOf(CampaignNotFoundException.class);

    verify(exportReadPort, never()).countEntities(CAMPAIGN_ID);
  }

  @Test
  @DisplayName("should throw ExportTooLargeException before any bulk read when over the cap")
  void export_overCap_throwsBeforeBulkRead() {
    when(campaignRepository.findById(CAMPAIGN_ID)).thenReturn(Optional.of(campaign()));
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.GM));
    when(exportReadPort.countEntities(CAMPAIGN_ID)).thenReturn((long) MAX_ENTITIES + 1);

    assertThatThrownBy(() -> service.export(CAMPAIGN_ID, CALLER_ID, false))
        .isInstanceOf(ExportTooLargeException.class);

    verify(exportReadPort, never()).readEntities(CAMPAIGN_ID);
    verify(exportReadPort, never()).readAnnotations(CAMPAIGN_ID);
    verify(exportReadPort, never()).readSessions(CAMPAIGN_ID);
  }
}
