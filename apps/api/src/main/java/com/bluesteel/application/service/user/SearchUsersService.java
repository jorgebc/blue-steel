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
 * Authorizes platform admins and any-campaign GMs to look up users by exact email so a GM can
 * invite an existing account (D-043, D-064).
 */
@Service
public class SearchUsersService implements SearchUsersUseCase {

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

    return userRepository.findByEmail(email).map(this::toProfile).map(List::of).orElseGet(List::of);
  }

  private UserProfile toProfile(User user) {
    return new UserProfile(user.id(), user.email(), user.isAdmin(), user.forcePasswordChange());
  }
}
