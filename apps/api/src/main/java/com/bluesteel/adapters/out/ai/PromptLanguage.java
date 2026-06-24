package com.bluesteel.adapters.out.ai;

import java.util.Locale;

/**
 * Maps a campaign content-language code (D-103) to an English display name used inside LLM prompt
 * instructions (e.g. {@code "Write ... in Spanish."}). Unknown or null codes fall back to English.
 */
final class PromptLanguage {

  private PromptLanguage() {}

  static String displayName(String code) {
    if (code == null) {
      return "English";
    }
    return switch (code.toLowerCase(Locale.ROOT)) {
      case "es" -> "Spanish";
      default -> "English";
    };
  }
}
