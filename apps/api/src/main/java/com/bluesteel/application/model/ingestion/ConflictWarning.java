package com.bluesteel.application.model.ingestion;

/**
 * A non-blocking warning surfaced in the diff review when an extracted entity's proposed state
 * contradicts established world-state facts (ARCHITECTURE §6.3).
 */
public record ConflictWarning(String entityName, String description) {}
