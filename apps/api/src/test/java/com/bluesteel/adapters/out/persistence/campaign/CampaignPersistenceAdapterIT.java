package com.bluesteel.adapters.out.persistence.campaign;

import static org.assertj.core.api.Assertions.assertThat;

import com.bluesteel.TestcontainersPostgresBaseIT;
import com.bluesteel.adapters.out.persistence.user.UserPersistenceAdapter;
import com.bluesteel.domain.campaign.Campaign;
import com.bluesteel.domain.campaign.CampaignMember;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.user.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("CampaignPersistenceAdapter")
class CampaignPersistenceAdapterIT extends TestcontainersPostgresBaseIT {

  @Autowired private CampaignPersistenceAdapter adapter;
  @Autowired private CampaignMembershipAdapter membershipAdapter;
  @Autowired private UserPersistenceAdapter userAdapter;

  @Test
  @DisplayName("should save and find a campaign by id")
  void saveAndFindById() {
    User creator = savedUser();
    Campaign campaign = campaign(creator.id());

    adapter.save(campaign);
    Optional<Campaign> found = adapter.findById(campaign.id());

    assertThat(found).isPresent();
    assertThat(found.get().id()).isEqualTo(campaign.id());
    assertThat(found.get().name()).isEqualTo(campaign.name());
    assertThat(found.get().createdBy()).isEqualTo(creator.id());
  }

  @Test
  @DisplayName("should return empty when campaign is not found by id")
  void findById_notFound_returnsEmpty() {
    assertThat(adapter.findById(UUID.randomUUID())).isEmpty();
  }

  @Test
  @DisplayName("should find all campaigns")
  void findAll_returnsSavedCampaigns() {
    User creator = savedUser();
    Campaign c1 = campaign(creator.id());
    Campaign c2 = campaign(creator.id());

    adapter.save(c1);
    adapter.save(c2);

    List<Campaign> all = adapter.findAll();
    assertThat(all).extracting(Campaign::id).contains(c1.id(), c2.id());
  }

  @Test
  @DisplayName("should find campaigns by member id")
  void findAllByMemberId_returnsMemberCampaigns() {
    User creator = savedUser();
    User member = savedUser();
    Campaign campaign = campaign(creator.id());
    adapter.save(campaign);

    membershipAdapter.save(
        CampaignMember.create(
            UUID.randomUUID(), campaign.id(), member.id(), CampaignRole.PLAYER, Instant.now()));

    List<Campaign> found = adapter.findAllByMemberId(member.id());
    assertThat(found).extracting(Campaign::id).contains(campaign.id());
  }

  @Test
  @DisplayName("should not find campaigns for a non-member user")
  void findAllByMemberId_nonMember_returnsEmpty() {
    User creator = savedUser();
    Campaign campaign = campaign(creator.id());
    adapter.save(campaign);

    List<Campaign> found = adapter.findAllByMemberId(UUID.randomUUID());
    assertThat(found).extracting(Campaign::id).doesNotContain(campaign.id());
  }

  private User savedUser() {
    UUID id = UUID.randomUUID();
    User user =
        User.create(
            id,
            id + "@example.com",
            "$2a$10$hash",
            false,
            false,
            Instant.now().truncatedTo(ChronoUnit.MICROS));
    userAdapter.save(user);
    return user;
  }

  private Campaign campaign(UUID createdBy) {
    return Campaign.create(
        UUID.randomUUID(),
        "Campaign-" + UUID.randomUUID(),
        createdBy,
        Instant.now().truncatedTo(ChronoUnit.MICROS));
  }
}
