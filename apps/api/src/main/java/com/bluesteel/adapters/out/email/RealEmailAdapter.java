package com.bluesteel.adapters.out.email;

import com.bluesteel.application.model.email.EmailMessage;
import com.bluesteel.application.port.out.email.EmailPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Stub for the real email provider (Resend). Active on {@code email-real} profile (D-075). Wire the
 * actual Resend client here when the provider is configured.
 */
@Component
@Profile("email-real")
public class RealEmailAdapter implements EmailPort {

  @Override
  public void send(EmailMessage message) {
    throw new UnsupportedOperationException(
        "Real email adapter not yet implemented. Activate 'email-real' profile only after wiring the provider.");
  }
}
