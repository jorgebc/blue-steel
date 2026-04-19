package com.bluesteel.application.port.out.email;

import com.bluesteel.application.model.email.EmailMessage;

/**
 * Sends transactional emails. Activated on the {@code email-real} Spring profile; the mock
 * implementation logs to console on {@code local} profile (D-075).
 */
public interface EmailPort {

  void send(EmailMessage message);
}
