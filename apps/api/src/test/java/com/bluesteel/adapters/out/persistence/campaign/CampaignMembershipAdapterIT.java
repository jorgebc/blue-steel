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

@DisplayName("CampaignMembershipAdapter")
class CampaignMembershipAdapterIT extends TestcontainersPostgresBaseIT {

  @Autowired private CampaignMembershipAdapter adapter;
  @Autowired private CampaignPersistenceAdapter campaignAdapter;
  @Autowired private UserPersistenceAdapter userAdapter;

  @Test
  @DisplayName("should save a member and resolve their role")
  void saveAndResolveRole_matchFound() {
    User user = savedUser();
    Campaign campaign = savedCampaign(user.id());

    adapter.save(
        CampaignMember.create(
            UUID.randomUUID(), campaign.id(), user.id(), CampaignRole.GM, Instant.now()));

    Optional<CampaignRole> role = adapter.resolveRole(campaign.id(), user.id());
    assertThat(role).isPresent().contains(CampaignRole.GM);
  }

  @Test
  @DisplayName("should return empty when user is not a member of the campaign")
  void resolveRole_noMembership_returnsEmpty() {
    Optional<CampaignRole> role = adapter.resolveRole(UUID.randomUUID(), UUID.randomUUID());
    assertThat(role).isEmpty();
  }

  @Test
  @DisplayName("should persist role as lowercase text compatible with DB CHECK constraint")
  void saveAndResolveRole_editorRole_mappedCorrectly() {
    User user = savedUser();
    Campaign campaign = savedCampaign(user.id());

    adapter.save(
        CampaignMember.create(
            UUID.randomUUID(), campaign.id(), user.id(), CampaignRole.EDITOR, Instant.now()));

    Optional<CampaignRole> role = adapter.resolveRole(campaign.id(), user.id());
    assertThat(role).isPresent().contains(CampaignRole.EDITOR);
  }

  @Test
  @DisplayName("should find a saved member by campaign and user id")
  void findByCampaignIdAndUserId_matchFound() {
    User user = savedUser();
    Campaign campaign = savedCampaign(user.id());
    adapter.save(
        CampaignMember.create(
            UUID.randomUUID(), campaign.id(), user.id(), CampaignRole.PLAYER, Instant.now()));

    Optional<CampaignMember> found = adapter.findByCampaignIdAndUserId(campaign.id(), user.id());

    assertThat(found).isPresent();
    assertThat(found.get().campaignId()).isEqualTo(campaign.id());
    assertThat(found.get().userId()).isEqualTo(user.id());
    assertThat(found.get().role()).isEqualTo(CampaignRole.PLAYER);
  }

  @Test
  @DisplayName("should return empty when finding a non-existent membership")
  void findByCampaignIdAndUserId_noMatch_returnsEmpty() {
    assertThat(adapter.findByCampaignIdAndUserId(UUID.randomUUID(), UUID.randomUUID())).isEmpty();
  }

  @Test
  @DisplayName("should delete a membership so the role no longer resolves")
  void deleteByCampaignIdAndUserId_removesRow() {
    User user = savedUser();
    Campaign campaign = savedCampaign(user.id());
    adapter.save(
        CampaignMember.create(
            UUID.randomUUID(), campaign.id(), user.id(), CampaignRole.PLAYER, Instant.now()));

    adapter.deleteByCampaignIdAndUserId(campaign.id(), user.id());

    assertThat(adapter.resolveRole(campaign.id(), user.id())).isEmpty();
  }

  @Test
  @DisplayName("should report true only when the user holds the given role in some campaign")
  void existsByUserIdAndRole_reflectsSavedRole() {
    User user = savedUser();
    Campaign campaign = savedCampaign(user.id());
    adapter.save(
        CampaignMember.create(
            UUID.randomUUID(), campaign.id(), user.id(), CampaignRole.GM, Instant.now()));

    assertThat(adapter.existsByUserIdAndRole(user.id(), CampaignRole.GM)).isTrue();
    assertThat(adapter.existsByUserIdAndRole(user.id(), CampaignRole.PLAYER)).isFalse();
    assertThat(adapter.existsByUserIdAndRole(UUID.randomUUID(), CampaignRole.GM)).isFalse();
  }

  @Test
  @DisplayName("should return all members of a campaign ordered by join time")
  void findByCampaignId_returnsAllMembers() {
    User gm = savedUser();
    Campaign campaign = savedCampaign(gm.id());
    adapter.save(
        CampaignMember.create(
            UUID.randomUUID(), campaign.id(), gm.id(), CampaignRole.GM, Instant.now()));
    User player = savedUser();
    adapter.save(
        CampaignMember.create(
            UUID.randomUUID(), campaign.id(), player.id(), CampaignRole.PLAYER, Instant.now()));

    List<CampaignMember> members = adapter.findByCampaignId(campaign.id());

    assertThat(members).hasSize(2);
    assertThat(members).extracting(CampaignMember::userId).contains(gm.id(), player.id());
  }

  @Test
  @DisplayName("should return an empty list for a campaign with no members")
  void findByCampaignId_noMembers_returnsEmpty() {
    assertThat(adapter.findByCampaignId(UUID.randomUUID())).isEmpty();
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

  private Campaign savedCampaign(UUID createdBy) {
    Campaign campaign =
        Campaign.create(
            UUID.randomUUID(),
            "Campaign-" + UUID.randomUUID(),
            createdBy,
            Instant.now().truncatedTo(ChronoUnit.MICROS));
    campaignAdapter.save(campaign);
    return campaign;
  }
}
