package com.bluesteel.application.model.ingestion;

/**
 * A relationship candidate extracted from a raw session narrative by the LLM (ARCHITECTURE §6.2).
 * Unlike a plain {@link ExtractedMention}, a relation carries its two endpoints as the
 * <em>names</em> of the entities it connects ({@code sourceMention} / {@code targetMention}); those
 * names are best-effort resolved to committed actor/space ids at commit time (F4.3.4, D-095).
 * Either mention may be null when the extractor cannot identify an endpoint.
 */
public record ExtractedRelation(
    String name, String description, String sourceMention, String targetMention, String rawText) {

  /** The name/description/rawText projection used for two-stage resolution and diff-card keying. */
  public ExtractedMention toMention() {
    return new ExtractedMention(name, description, rawText);
  }
}
