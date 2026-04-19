package com.bluesteel.adapters.out.persistence.session;

import com.bluesteel.application.port.out.session.SessionRecoveryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** JdbcTemplate-backed implementation of {@link SessionRecoveryPort}. */
@Component
public class SessionRecoveryAdapter implements SessionRecoveryPort {

  private static final Logger log = LoggerFactory.getLogger(SessionRecoveryAdapter.class);

  private final JdbcTemplate jdbcTemplate;

  public SessionRecoveryAdapter(@Lazy JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public int recoverStuckSessions() {
    try {
      return jdbcTemplate.update(
          """
          UPDATE sessions
          SET status = 'failed',
              failure_reason = 'PIPELINE_INTERRUPTED',
              updated_at = now()
          WHERE status = 'processing'
          """);
    } catch (BadSqlGrammarException e) {
      log.debug("Sessions table not yet present — skipping stuck-session recovery");
      return -1;
    }
  }
}
