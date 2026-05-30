package com.bluesteel.application.port.out.ingestion;

import com.bluesteel.application.model.ingestion.ExtractionResult;

/**
 * Driven port: extracts structured entity candidates from raw session text via an LLM call.
 *
 * <p>Takes {@code String rawSummaryText} (not {@code NarrativeBlock}) because this port is defined
 * in F2.2 before the {@code NarrativeBlock} domain class exists; F2.4 passes {@code
 * narrativeBlock.rawSummaryText()} when wiring the real pipeline.
 */
public interface NarrativeExtractionPort {

  ExtractionResult extract(String rawSummaryText);
}
