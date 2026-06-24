package com.bluesteel.application.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.bluesteel.application.model.campaign.CampaignView;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.campaign.CampaignRepository;
import com.bluesteel.application.service.campaign.GetCampaignService;
import com.bluesteel.domain.campaign.Campaign;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.CampaignNotFoundException;
import com.bluesteel.domain.exception.UnauthorizedException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetCampaignService")
class GetCampaignServiceTest {

  @Mock private CampaignRepository campaignRepository;
  @Mock private CampaignMembershipPort membershipPort;

  private GetCampaignService sut;

  private static final UUID CAMPAIGN_ID = UUID.randomUUID();
  private static final UUID CALLER_ID = UUID.randomUUID();
  private static final Campaign CAMPAIGN =
      Campaign.create(CAMPAIGN_ID, "Dragon Keep", UUID.randomUUID(), Instant.now(), "es");

  @BeforeEach
  void setUp() {
    sut = new GetCampaignService(campaignRepository, membershipPort);
  }

  @Test
  @DisplayName("should throw CampaignNotFoundException when campaign does not exist")
  void get_campaignNotFound_throwsCampaignNotFound() {
    when(campaignRepository.findById(CAMPAIGN_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> sut.get(CAMPAIGN_ID, CALLER_ID, false))
        .isInstanceOf(CampaignNotFoundException.class);
  }

  @Test
  @DisplayName("should throw UnauthorizedException when non-admin caller is not a member")
  void get_nonMemberNonAdmin_throwsUnauthorized() {
    when(campaignRepository.findById(CAMPAIGN_ID)).thenReturn(Optional.of(CAMPAIGN));
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> sut.get(CAMPAIGN_ID, CALLER_ID, false))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  @DisplayName("should return campaign view with role when caller is a member")
  void get_memberCaller_returnsCampaignViewWithRole() {
    when(campaignRepository.findById(CAMPAIGN_ID)).thenReturn(Optional.of(CAMPAIGN));
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.GM));

    CampaignView view = sut.get(CAMPAIGN_ID, CALLER_ID, false);

    assertThat(view.id()).isEqualTo(CAMPAIGN_ID);
    assertThat(view.role()).isEqualTo(CampaignRole.GM);
    assertThat(view.contentLanguage()).isEqualTo("es");
  }

  @Test
  @DisplayName("should return campaign view with null role when admin caller is not a member")
  void get_adminNonMember_returnsCampaignViewWithNullRole() {
    when(campaignRepository.findById(CAMPAIGN_ID)).thenReturn(Optional.of(CAMPAIGN));
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID)).thenReturn(Optional.empty());

    CampaignView view = sut.get(CAMPAIGN_ID, CALLER_ID, true);

    assertThat(view.id()).isEqualTo(CAMPAIGN_ID);
    assertThat(view.role()).isNull();
  }
}
