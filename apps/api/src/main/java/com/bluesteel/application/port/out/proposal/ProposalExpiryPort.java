package com.bluesteel.application.port.out.proposal;

import java.time.Instant;

/** Driven port for bulk-expiring proposals whose TTL has elapsed (D-019). */
public interface ProposalExpiryPort {

  /**
   * Flips every {@code open} or {@code cosigned} proposal whose {@code expires_at} is before {@code
   * cutoff} to {@code expired} in one statement, returning the number of rows updated (or {@code
   * -1} if the proposals table does not yet exist).
   */
  int expireProposals(Instant cutoff);
}
