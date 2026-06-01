package com.bluesteel.application.service.user;

import com.bluesteel.application.model.user.UserProfile;
import com.bluesteel.application.port.in.user.SearchUsersUseCase;
import com.bluesteel.application.port.out.campaign.CampaignMembershipRepository;
import com.bluesteel.application.port.out.user.UserRepository;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.UnauthorizedException;
import com.bluesteel.domain.user.User;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Authorizes platform admins and any-campaign GMs to look up users by partial email (typeahead) so
 * a GM can be picked or invited without typing a full address (D-043, D-064).
 */
@Service
public class SearchUsersService implements SearchUsersUseCase {

  /** Minimum fragment length before searching — avoids scanning the whole table on a stray key. */
  private static final int MIN_FRAGMENT_LENGTH = 2;

  private final UserRepository userRepository;
  private final CampaignMembershipRepository campaignMembershipRepository;

  public SearchUsersService(
      UserRepository userRepository, CampaignMembershipRepository campaignMembershipRepository) {
    this.userRepository = userRepository;
    this.campaignMembershipRepository = campaignMembershipRepository;
  }

  @Override
  public List<UserProfile> searchByEmail(String email, UUID callerId, boolean callerIsAdmin) {
    if (!callerIsAdmin
        && !campaignMembershipRepository.existsByUserIdAndRole(callerId, CampaignRole.GM)) {
      throw new UnauthorizedException("Only an admin or a campaign GM may search users");
    }

    String fragment = email == null ? "" : email.strip();
    if (fragment.length() < MIN_FRAGMENT_LENGTH) {
      return List.of();
    }

    return userRepository.searchByEmail(fragment).stream().map(this::toProfile).toList();
  }

  private UserProfile toProfile(User user) {
    return new UserProfile(user.id(), user.email(), user.isAdmin(), user.forcePasswordChange());
  }
}
