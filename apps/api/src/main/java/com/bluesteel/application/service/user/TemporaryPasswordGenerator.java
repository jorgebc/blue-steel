package com.bluesteel.application.service.user;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** Generates cryptographically random temporary passwords for invitation flows (D-077). */
@Component
public class TemporaryPasswordGenerator {

  private static final String PASSWORD_CHARS =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
  private static final int PASSWORD_LENGTH = 16;
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  public String generate() {
    StringBuilder sb = new StringBuilder(PASSWORD_LENGTH);
    for (int i = 0; i < PASSWORD_LENGTH; i++) {
      sb.append(PASSWORD_CHARS.charAt(SECURE_RANDOM.nextInt(PASSWORD_CHARS.length())));
    }
    return sb.toString();
  }
}
