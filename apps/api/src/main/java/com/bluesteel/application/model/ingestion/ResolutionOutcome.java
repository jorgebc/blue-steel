package com.bluesteel.application.model.ingestion;

/** The outcome of matching an {@link ExtractedMention} against existing world-state entities. */
public enum ResolutionOutcome {
  /** The mention matches an existing entity with sufficient confidence. */
  MATCH,
  /** The mention is a new entity not seen before. */
  NEW,
  /** Confidence is too low to decide; human review required before commit (D-042). */
  UNCERTAIN
}
