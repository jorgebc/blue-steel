package com.bluesteel.application.model.ingestion;

import java.util.List;
import java.util.Objects;

/**
 * The structured output of {@code NarrativeExtractionPort}: a narrative summary header plus four
 * entity-candidate lists (actors, spaces, events, relations) extracted from one session's raw text.
 * Relations are {@link ExtractedRelation}s carrying structured source/target endpoint mentions
 * (D-095).
 */
public record ExtractionResult(
    String narrativeSummaryHeader,
    List<ExtractedMention> actors,
    List<ExtractedMention> spaces,
    List<ExtractedMention> events,
    List<ExtractedRelation> relations) {

  public ExtractionResult {
    Objects.requireNonNull(narrativeSummaryHeader, "narrativeSummaryHeader must not be null");
    if (narrativeSummaryHeader.isBlank()) {
      throw new IllegalArgumentException("narrativeSummaryHeader must not be blank");
    }
    Objects.requireNonNull(actors, "actors must not be null");
    Objects.requireNonNull(spaces, "spaces must not be null");
    Objects.requireNonNull(events, "events must not be null");
    Objects.requireNonNull(relations, "relations must not be null");
  }
}
