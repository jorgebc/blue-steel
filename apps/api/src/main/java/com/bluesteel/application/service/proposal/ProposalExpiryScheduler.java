package com.bluesteel.application.service.proposal;

import com.bluesteel.application.port.out.proposal.ProposalExpiryPort;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically expires proposals whose TTL has elapsed (D-019). The TTL itself is owned by {@code
 * ProposalCreationService} (stamped into {@code expires_at} at creation); this scheduler only owns
 * the sweep cadence and compares against the current instant.
 */
@Component
public class ProposalExpiryScheduler {

  private static final Logger log = LoggerFactory.getLogger(ProposalExpiryScheduler.class);

  private final ProposalExpiryPort proposalExpiryPort;

  public ProposalExpiryScheduler(ProposalExpiryPort proposalExpiryPort) {
    this.proposalExpiryPort = proposalExpiryPort;
  }

  @Scheduled(fixedDelayString = "${blue-steel.proposal.expiry-sweep-interval-ms:300000}")
  public void expireStaleProposals() {
    int count = proposalExpiryPort.expireProposals(Instant.now());
    if (count > 0) {
      log.info("Expired {} stale proposal(s)", count);
    }
  }
}
