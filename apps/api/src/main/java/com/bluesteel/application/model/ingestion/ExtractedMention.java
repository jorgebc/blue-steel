package com.bluesteel.application.model.ingestion;

/**
 * A single entity candidate extracted from a raw session narrative by the LLM (ARCHITECTURE §6.2).
 * Passed to {@code EntityResolutionPort} to determine whether it matches an existing world-state
 * entity.
 */
public record ExtractedMention(String name, String description, String rawText) {}
