package com.bluesteel.application.event;

import java.util.UUID;

/** Published after a session is persisted in {@code PENDING} status and ready for ingestion. */
public record SessionSubmittedEvent(UUID sessionId, UUID campaignId) {}
