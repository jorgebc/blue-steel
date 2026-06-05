package com.bluesteel.application.model.ingestion;

import java.util.List;

/**
 * An event candidate extracted from a raw session narrative by the LLM (ARCHITECTURE §6.2). Unlike
 * a plain {@link ExtractedMention}, an event carries the structured links the Timeline and
 * cross-links rely on: the <em>name</em> of the space it occurred in ({@code spaceMention}), the
 * <em>names</em> of the actors it involved ({@code involvedActorMentions}), and an {@code
 * eventType} label (D-097). Those mentions are best-effort resolved to committed space/actor ids at
 * commit time (F4.6.4, D-095); {@code spaceMention} may be null and {@code involvedActorMentions}
 * empty when the extractor cannot identify them, and {@code eventType} is null when the type is
 * unclear.
 */
public record ExtractedEvent(
    String name,
    String description,
    String eventType,
    String spaceMention,
    List<String> involvedActorMentions,
    String rawText) {

  public ExtractedEvent {
    involvedActorMentions =
        involvedActorMentions == null ? List.of() : List.copyOf(involvedActorMentions);
  }

  /** The name/description/rawText projection used for two-stage resolution and diff-card keying. */
  public ExtractedMention toMention() {
    return new ExtractedMention(name, description, rawText);
  }
}
