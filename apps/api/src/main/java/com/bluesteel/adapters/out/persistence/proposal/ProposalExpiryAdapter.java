package com.bluesteel.adapters.out.persistence.proposal;

import com.bluesteel.application.port.out.proposal.ProposalExpiryPort;
import java.sql.Timestamp;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** JdbcTemplate-backed implementation of {@link ProposalExpiryPort} (D-019). */
@Component
public class ProposalExpiryAdapter implements ProposalExpiryPort {

  private static final Logger log = LoggerFactory.getLogger(ProposalExpiryAdapter.class);

  private final JdbcTemplate jdbcTemplate;

  public ProposalExpiryAdapter(@Lazy JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public int expireProposals(Instant cutoff) {
    try {
      return jdbcTemplate.update(
          """
          UPDATE proposals
          SET status = 'expired'
          WHERE status IN ('open', 'cosigned')
            AND expires_at < ?
          """,
          Timestamp.from(cutoff));
    } catch (BadSqlGrammarException e) {
      log.debug("Proposals table not yet present — skipping proposal expiry sweep");
      return -1;
    }
  }
}
