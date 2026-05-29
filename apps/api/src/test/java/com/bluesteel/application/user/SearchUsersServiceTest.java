package com.bluesteel.application.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluesteel.application.model.user.UserProfile;
import com.bluesteel.application.port.out.campaign.CampaignMembershipRepository;
import com.bluesteel.application.port.out.user.UserRepository;
import com.bluesteel.application.service.user.SearchUsersService;
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
@DisplayName("SearchUsersService")
class SearchUsersServiceTest {

  private static final UUID CALLER_ID = UUID.randomUUID();
  private static final String EMAIL = "found@example.com";

  @Mock private UserRepository userRepository;
  @Mock private CampaignMembershipRepository campaignMembershipRepository;

  private SearchUsersService service;

  @BeforeEach
  void setUp() {
    service = new SearchUsersService(userRepository, campaignMembershipRepository);
  }

  private User user() {
    return User.create(UUID.randomUUID(), EMAIL, "$2a$10$hash", false, false, Instant.now());
  }

  @Test
  @DisplayName("should return the matching user when caller is an admin")
  void searchByEmail_adminMatch_returnsUser() {
    User found = user();
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(found));

    List<UserProfile> result = service.searchByEmail(EMAIL, CALLER_ID, true);

    assertThat(result).singleElement().satisfies(p -> assertThat(p.email()).isEqualTo(EMAIL));
  }

  @Test
  @DisplayName("should return an empty list when admin search finds no user")
  void searchByEmail_adminNoMatch_returnsEmpty() {
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

    assertThat(service.searchByEmail(EMAIL, CALLER_ID, true)).isEmpty();
  }

  @Test
  @DisplayName("should return the matching user when caller is a GM of any campaign")
  void searchByEmail_gmAnywhereMatch_returnsUser() {
    when(campaignMembershipRepository.existsByUserIdAndRole(CALLER_ID, CampaignRole.GM))
        .thenReturn(true);
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user()));

    assertThat(service.searchByEmail(EMAIL, CALLER_ID, false)).hasSize(1);
  }

  @Test
  @DisplayName("should throw UnauthorizedException when caller is neither admin nor a GM")
  void searchByEmail_notAdminNotGm_throwsUnauthorized() {
    when(campaignMembershipRepository.existsByUserIdAndRole(CALLER_ID, CampaignRole.GM))
        .thenReturn(false);

    assertThatThrownBy(() -> service.searchByEmail(EMAIL, CALLER_ID, false))
        .isInstanceOf(UnauthorizedException.class);

    verify(userRepository, never()).findByEmail(EMAIL);
  }
}
