package com.bluesteel.domain.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CampaignMember domain entity")
class CampaignMemberTest {

  private static final UUID ID = UUID.randomUUID();
  private static final UUID CAMPAIGN_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();
  private static final Instant NOW = Instant.now();

  @Test
  @DisplayName("should create a campaign member with valid fields")
  void create_validFields_succeeds() {
    CampaignMember member = CampaignMember.create(ID, CAMPAIGN_ID, USER_ID, CampaignRole.GM, NOW);

    assertThat(member.id()).isEqualTo(ID);
    assertThat(member.campaignId()).isEqualTo(CAMPAIGN_ID);
    assertThat(member.userId()).isEqualTo(USER_ID);
    assertThat(member.role()).isEqualTo(CampaignRole.GM);
    assertThat(member.joinedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("should reject a null role")
  void create_nullRole_throwsIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> CampaignMember.create(ID, CAMPAIGN_ID, USER_ID, null, NOW));
  }

  @Test
  @DisplayName("should return a copy with the new role and all other fields preserved")
  void withRole_changesRoleOnly() {
    CampaignMember original =
        CampaignMember.create(ID, CAMPAIGN_ID, USER_ID, CampaignRole.PLAYER, NOW);

    CampaignMember updated = original.withRole(CampaignRole.EDITOR);

    assertThat(updated.role()).isEqualTo(CampaignRole.EDITOR);
    assertThat(updated.id()).isEqualTo(ID);
    assertThat(updated.campaignId()).isEqualTo(CAMPAIGN_ID);
    assertThat(updated.userId()).isEqualTo(USER_ID);
    assertThat(updated.joinedAt()).isEqualTo(NOW);
    assertThat(original.role()).isEqualTo(CampaignRole.PLAYER);
  }
}
