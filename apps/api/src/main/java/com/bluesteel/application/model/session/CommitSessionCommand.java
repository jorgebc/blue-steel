package com.bluesteel.application.model.session;

import com.bluesteel.application.model.commit.CommitPayload;
import java.util.UUID;

/** Input for {@link com.bluesteel.application.port.in.session.CommitSessionUseCase}. */
public record CommitSessionCommand(
    UUID callerId, UUID campaignId, UUID sessionId, CommitPayload payload) {}
