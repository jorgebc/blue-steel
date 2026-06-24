package com.bluesteel.application.port.out.ingestion;

import com.bluesteel.application.model.ingestion.ExtractionResult;

/**
 * Driven port: extracts structured entity candidates from raw session text via an LLM call.
 *
 * <p>Takes {@code String rawSummaryText} (not {@code NarrativeBlock}) because this port is defined
 * in F2.2 before the {@code NarrativeBlock} domain class exists; F2.4 passes {@code
 * narrativeBlock.rawSummaryText()} when wiring the real pipeline.
 *
 * <p>Relations are returned as {@code ExtractedRelation}s carrying the names of their source and
 * target entities, and events as {@code ExtractedEvent}s carrying the name of their space, the
 * names of their involved actors, and an event type; those mentions are resolved to entity ids at
 * commit (D-095, D-097).
 *
 * <p>{@code contentLanguage} is the campaign's immutable content-language code ({@code en}/{@code
 * es}, D-103); the real adapter instructs the LLM to write extracted names, descriptions, and the
 * summary in that language.
 */
public interface NarrativeExtractionPort {

  ExtractionResult extract(String rawSummaryText, String contentLanguage);
}
