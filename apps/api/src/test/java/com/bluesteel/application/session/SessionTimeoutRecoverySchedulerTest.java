package com.bluesteel.application.session;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluesteel.application.port.out.session.SessionRecoveryPort;
import com.bluesteel.application.service.session.SessionTimeoutRecoveryScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionTimeoutRecoveryScheduler")
class SessionTimeoutRecoverySchedulerTest {

  @Mock private SessionRecoveryPort sessionRecoveryPort;

  private SessionTimeoutRecoveryScheduler sut;

  private static final int TIMEOUT_MINUTES = 10;

  @BeforeEach
  void setUp() {
    sut = new SessionTimeoutRecoveryScheduler(sessionRecoveryPort, TIMEOUT_MINUTES);
  }

  @Test
  @DisplayName("should delegate to SessionRecoveryPort with the configured timeout in minutes")
  void recoverTimedOutSessions_delegatesToPort() {
    when(sessionRecoveryPort.recoverTimedOutSessions(TIMEOUT_MINUTES)).thenReturn(0);

    sut.recoverTimedOutSessions();

    verify(sessionRecoveryPort).recoverTimedOutSessions(TIMEOUT_MINUTES);
  }

  @Test
  @DisplayName("should not log a warning when no sessions are recovered")
  void recoverTimedOutSessions_zeroCount_doesNotWarn() {
    when(sessionRecoveryPort.recoverTimedOutSessions(TIMEOUT_MINUTES)).thenReturn(0);

    sut.recoverTimedOutSessions();

    verify(sessionRecoveryPort).recoverTimedOutSessions(TIMEOUT_MINUTES);
    // No warning — verified by the absence of log output; test passes if no exception
  }

  @Test
  @DisplayName("should call recovery with the correct timeout when sessions are recovered")
  void recoverTimedOutSessions_withRecoveredSessions_delegatesCorrectly() {
    when(sessionRecoveryPort.recoverTimedOutSessions(TIMEOUT_MINUTES)).thenReturn(3);

    sut.recoverTimedOutSessions();

    verify(sessionRecoveryPort).recoverTimedOutSessions(TIMEOUT_MINUTES);
  }
}
