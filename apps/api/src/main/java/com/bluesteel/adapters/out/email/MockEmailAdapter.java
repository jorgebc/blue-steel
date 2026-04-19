package com.bluesteel.adapters.out.email;

import com.bluesteel.application.model.email.EmailMessage;
import com.bluesteel.application.port.out.email.EmailPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Logs emails to the console instead of sending them (D-075). Active on {@code local} profile. */
@Component
@Profile("local")
public class MockEmailAdapter implements EmailPort {

  private static final Logger log = LoggerFactory.getLogger(MockEmailAdapter.class);

  @Override
  public void send(EmailMessage message) {
    log.info(
        "[MOCK EMAIL] to={} subject='{}' body={}", message.to(), message.subject(), message.body());
  }
}
