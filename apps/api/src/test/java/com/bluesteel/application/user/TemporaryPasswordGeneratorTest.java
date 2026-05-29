package com.bluesteel.application.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.bluesteel.application.service.user.TemporaryPasswordGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TemporaryPasswordGenerator")
class TemporaryPasswordGeneratorTest {

  private final TemporaryPasswordGenerator generator = new TemporaryPasswordGenerator();

  @Test
  @DisplayName("should generate a 16-character password drawn only from the allowed charset")
  void generate_returnsSixteenCharsFromAllowedCharset() {
    String allowed = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";

    String password = generator.generate();

    assertThat(password).hasSize(16);
    assertThat(password.chars()).allMatch(c -> allowed.indexOf(c) >= 0);
  }

  @Test
  @DisplayName("should generate a different password on each call")
  void generate_producesDistinctValues() {
    assertThat(generator.generate()).isNotEqualTo(generator.generate());
  }
}
