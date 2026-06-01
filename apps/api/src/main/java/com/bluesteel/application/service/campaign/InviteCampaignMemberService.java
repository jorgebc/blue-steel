package com.bluesteel.application.service.campaign;

import com.bluesteel.application.model.campaign.InviteCampaignMemberCommand;
import com.bluesteel.application.port.in.campaign.InviteCampaignMemberUseCase;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.campaign.CampaignMembershipRepository;
import com.bluesteel.application.port.out.email.EmailPort;
import com.bluesteel.application.port.out.user.UserRepository;
import com.bluesteel.application.service.email.InvitationEmailFactory;
import com.bluesteel.application.service.user.TemporaryPasswordGenerator;
import com.bluesteel.domain.campaign.CampaignMember;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.AlreadyCampaignMemberException;
import com.bluesteel.domain.exception.UnauthorizedException;
import com.bluesteel.domain.user.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GM-only: creates or refreshes the invited user's account and adds them to the campaign in one
 * transaction (D-064, D-075, D-077). Role resolution reads from the database on every call (D-043).
 */
@Service
public class InviteCampaignMemberService implements InviteCampaignMemberUseCase {

  private static final Logger log = LoggerFactory.getLogger(InviteCampaignMemberService.class);

  private final CampaignMembershipPort membershipPort;
  private final CampaignMembershipRepository membershipRepository;
  private final UserRepository userRepository;
  private final EmailPort emailPort;
  private final PasswordEncoder passwordEncoder;
  private final TemporaryPasswordGenerator temporaryPasswordGenerator;
  private final InvitationEmailFactory invitationEmailFactory;

  public InviteCampaignMemberService(
      CampaignMembershipPort membershipPort,
      CampaignMembershipRepository membershipRepository,
      UserRepository userRepository,
      EmailPort emailPort,
      PasswordEncoder passwordEncoder,
      TemporaryPasswordGenerator temporaryPasswordGenerator,
      InvitationEmailFactory invitationEmailFactory) {
    this.membershipPort = membershipPort;
    this.membershipRepository = membershipRepository;
    this.userRepository = userRepository;
    this.emailPort = emailPort;
    this.passwordEncoder = passwordEncoder;
    this.temporaryPasswordGenerator = temporaryPasswordGenerator;
    this.invitationEmailFactory = invitationEmailFactory;
  }

  @Override
  @Transactional
  public boolean invite(InviteCampaignMemberCommand command) {
    requireGm(command.campaignId(), command.callerId());

    String tempPassword = temporaryPasswordGenerator.generate();
    String passwordHash = passwordEncoder.encode(tempPassword);

    Optional<User> existing = userRepository.findByEmail(command.email());
    User user;
    boolean created;
    if (existing.isPresent()) {
      user = existing.get().withRefreshedInvitation(passwordHash);
      created = false;
    } else {
      user =
          User.create(UUID.randomUUID(), command.email(), passwordHash, false, true, Instant.now());
      created = true;
    }
    userRepository.save(user);

    if (membershipRepository
        .findByCampaignIdAndUserId(command.campaignId(), user.id())
        .isPresent()) {
      throw new AlreadyCampaignMemberException(command.campaignId(), user.id());
    }

    membershipRepository.save(
        CampaignMember.create(
            UUID.randomUUID(), command.campaignId(), user.id(), command.role(), Instant.now()));

    emailPort.send(invitationEmailFactory.campaignInvitation(command.email(), tempPassword));

    log.info(
        "Invited member email={} campaignId={} role={} created={}",
        command.email(),
        command.campaignId(),
        command.role(),
        created);
    return created;
  }

  private void requireGm(UUID campaignId, UUID callerId) {
    CampaignRole role =
        membershipPort
            .resolveRole(campaignId, callerId)
            .orElseThrow(
                () -> new UnauthorizedException("Caller is not a member of this campaign"));
    if (role != CampaignRole.GM) {
      throw new UnauthorizedException("Only the GM may invite campaign members");
    }
  }
}
