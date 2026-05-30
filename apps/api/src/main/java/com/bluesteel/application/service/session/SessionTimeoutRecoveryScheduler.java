package com.bluesteel.application.service.session;

import com.bluesteel.application.port.out.session.SessionRecoveryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically fails sessions stuck in {@code processing} past the configured timeout (D-074). */
@Component
public class SessionTimeoutRecoveryScheduler {

  private static final Logger log = LoggerFactory.getLogger(SessionTimeoutRecoveryScheduler.class);

  private final SessionRecoveryPort sessionRecoveryPort;
  private final int timeoutMinutes;

  public SessionTimeoutRecoveryScheduler(
      SessionRecoveryPort sessionRecoveryPort,
      @Value("${blue-steel.ingestion.processing-timeout-minutes:10}") int timeoutMinutes) {
    this.sessionRecoveryPort = sessionRecoveryPort;
    this.timeoutMinutes = timeoutMinutes;
  }

  @Scheduled(
      fixedDelayString = "${blue-steel.ingestion.processing-timeout-check-interval-ms:300000}")
  public void recoverTimedOutSessions() {
    int count = sessionRecoveryPort.recoverTimedOutSessions(timeoutMinutes);
    if (count > 0) {
      log.warn(
          "Recovered {} session(s) timed out in processing (timeout={}min)", count, timeoutMinutes);
    }
  }
}
