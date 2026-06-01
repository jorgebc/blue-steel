package com.bluesteel.application.campaign;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluesteel.application.port.out.campaign.CampaignRepository;
import com.bluesteel.application.service.campaign.DeleteCampaignService;
import com.bluesteel.domain.campaign.Campaign;
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
@DisplayName("DeleteCampaignService")
class DeleteCampaignServiceTest {

  private static final UUID CAMPAIGN_ID = UUID.randomUUID();
  private static final UUID CALLER_ID = UUID.randomUUID();

  @Mock private CampaignRepository campaignRepository;

  private DeleteCampaignService service;

  @BeforeEach
  void setUp() {
    service = new DeleteCampaignService(campaignRepository);
  }

  @Test
  @DisplayName("should throw UnauthorizedException when caller is not an admin")
  void delete_callerNotAdmin_throwsUnauthorized() {
    assertThatThrownBy(() -> service.delete(CAMPAIGN_ID, CALLER_ID, false))
        .isInstanceOf(UnauthorizedException.class);

    verify(campaignRepository, never()).deleteById(CAMPAIGN_ID);
  }

  @Test
  @DisplayName("should throw CampaignNotFoundException when campaign does not exist")
  void delete_campaignNotFound_throwsNotFound() {
    when(campaignRepository.findById(CAMPAIGN_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete(CAMPAIGN_ID, CALLER_ID, true))
        .isInstanceOf(CampaignNotFoundException.class);

    verify(campaignRepository, never()).deleteById(CAMPAIGN_ID);
  }

  @Test
  @DisplayName("should delete the campaign when caller is admin and campaign exists")
  void delete_adminAndCampaignExists_deletesCampaign() {
    Campaign campaign = Campaign.create(CAMPAIGN_ID, "Dragon Keep", CALLER_ID, Instant.now());
    when(campaignRepository.findById(CAMPAIGN_ID)).thenReturn(Optional.of(campaign));

    service.delete(CAMPAIGN_ID, CALLER_ID, true);

    verify(campaignRepository).deleteById(CAMPAIGN_ID);
  }
}
