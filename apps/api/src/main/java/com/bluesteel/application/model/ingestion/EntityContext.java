package com.bluesteel.application.model.ingestion;

import java.util.UUID;

/**
 * A read-only snapshot of a world-state entity used as context for LLM calls (ARCHITECTURE §6.2).
 * Built by use cases from repository data before any port call; never constructed inside an
 * adapter.
 */
public record EntityContext(
    UUID entityId,
    String entityType,
    String name,
    String stateSnapshot,
    UUID sessionId,
    int versionNumber) {}
