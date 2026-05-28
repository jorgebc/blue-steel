package com.bluesteel.application.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.bluesteel.application.model.campaign.CampaignView;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.campaign.CampaignRepository;
import com.bluesteel.application.service.campaign.ListCampaignsService;
import com.bluesteel.domain.campaign.Campaign;
import com.bluesteel.domain.campaign.CampaignRole;
import java.time.Instant;
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
@DisplayName("ListCampaignsService")
class ListCampaignsServiceTest {

  @Mock private CampaignRepository campaignRepository;
  @Mock private CampaignMembershipPort membershipPort;

  private ListCampaignsService sut;

  private static final UUID CALLER_ID = UUID.randomUUID();
  private static final UUID CAMPAIGN_ID_1 = UUID.randomUUID();
  private static final UUID CAMPAIGN_ID_2 = UUID.randomUUID();

  private final Campaign campaign1 =
      Campaign.create(CAMPAIGN_ID_1, "Dragon Keep", UUID.randomUUID(), Instant.now());
  private final Campaign campaign2 =
      Campaign.create(CAMPAIGN_ID_2, "Sea of Storms", UUID.randomUUID(), Instant.now());

  @BeforeEach
  void setUp() {
    sut = new ListCampaignsService(campaignRepository, membershipPort);
  }

  @Test
  @DisplayName("should return all campaigns with resolved-or-null role when caller is admin")
  void list_adminCaller_returnsAllCampaigns() {
    when(campaignRepository.findAll()).thenReturn(List.of(campaign1, campaign2));
    when(membershipPort.resolveRole(CAMPAIGN_ID_1, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.GM));
    when(membershipPort.resolveRole(CAMPAIGN_ID_2, CALLER_ID)).thenReturn(Optional.empty());

    List<CampaignView> result = sut.list(CALLER_ID, true);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).role()).isEqualTo(CampaignRole.GM);
    assertThat(result.get(1).role()).isNull();
  }

  @Test
  @DisplayName("should return only caller's campaigns when caller is not admin")
  void list_nonAdminCaller_returnsOnlyMemberCampaigns() {
    when(campaignRepository.findAllByMemberId(CALLER_ID)).thenReturn(List.of(campaign1));
    when(membershipPort.resolveRole(CAMPAIGN_ID_1, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));

    List<CampaignView> result = sut.list(CALLER_ID, false);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).id()).isEqualTo(CAMPAIGN_ID_1);
    assertThat(result.get(0).role()).isEqualTo(CampaignRole.PLAYER);
  }

  @Test
  @DisplayName("should return empty list when caller has no campaigns")
  void list_nonAdminWithNoCampaigns_returnsEmptyList() {
    when(campaignRepository.findAllByMemberId(CALLER_ID)).thenReturn(List.of());

    List<CampaignView> result = sut.list(CALLER_ID, false);

    assertThat(result).isEmpty();
  }
}
