package com.bluesteel.application.model.session;

import java.util.UUID;

/** Command for submitting a raw session narrative for ingestion. */
public record SubmitSessionCommand(UUID callerId, UUID campaignId, String summaryText) {}
