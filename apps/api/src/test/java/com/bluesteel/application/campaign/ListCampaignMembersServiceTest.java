package com.bluesteel.application.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.bluesteel.application.model.campaign.CampaignMemberView;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.campaign.CampaignMembershipRepository;
import com.bluesteel.application.port.out.user.UserRepository;
import com.bluesteel.application.service.campaign.ListCampaignMembersService;
import com.bluesteel.domain.campaign.CampaignMember;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.UnauthorizedException;
import com.bluesteel.domain.user.User;
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
@DisplayName("ListCampaignMembersService")
class ListCampaignMembersServiceTest {

  @Mock private CampaignMembershipPort membershipPort;
  @Mock private CampaignMembershipRepository membershipRepository;
  @Mock private UserRepository userRepository;

  private ListCampaignMembersService sut;

  private static final UUID CALLER_ID = UUID.randomUUID();
  private static final UUID CAMPAIGN_ID = UUID.randomUUID();
  private static final UUID MEMBER_ID = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    sut = new ListCampaignMembersService(membershipPort, membershipRepository, userRepository);
  }

  @Test
  @DisplayName("should throw UnauthorizedException when a non-admin caller is not a member")
  void list_nonMemberNonAdmin_throwsUnauthorized() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> sut.list(CAMPAIGN_ID, CALLER_ID, false))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  @DisplayName("should return the roster with member emails for a campaign member")
  void list_member_returnsRosterWithEmails() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    Instant joinedAt = Instant.now();
    CampaignMember member =
        CampaignMember.create(
            UUID.randomUUID(), CAMPAIGN_ID, MEMBER_ID, CampaignRole.EDITOR, joinedAt);
    when(membershipRepository.findByCampaignId(CAMPAIGN_ID)).thenReturn(List.of(member));
    User user = User.create(MEMBER_ID, "editor@example.com", "$2a$10$hash", false, false, joinedAt);
    when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(user));

    List<CampaignMemberView> roster = sut.list(CAMPAIGN_ID, CALLER_ID, false);

    assertThat(roster).hasSize(1);
    assertThat(roster.get(0).userId()).isEqualTo(MEMBER_ID);
    assertThat(roster.get(0).email()).isEqualTo("editor@example.com");
    assertThat(roster.get(0).role()).isEqualTo(CampaignRole.EDITOR);
  }

  @Test
  @DisplayName("should allow a platform admin who is not a campaign member")
  void list_adminNonMember_returnsRoster() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID)).thenReturn(Optional.empty());
    when(membershipRepository.findByCampaignId(CAMPAIGN_ID)).thenReturn(List.of());

    List<CampaignMemberView> roster = sut.list(CAMPAIGN_ID, CALLER_ID, true);

    assertThat(roster).isEmpty();
  }
}
