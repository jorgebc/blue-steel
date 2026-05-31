package com.bluesteel.application.event;

import com.bluesteel.application.model.worldstate.CommittedEntityVersion;
import java.util.List;
import java.util.UUID;

/**
 * Published after the commit transaction commits. Carries the committed entity versions so the
 * async listener can generate embeddings without re-reading the DB (D-063).
 */
public record SessionCommittedEvent(
    UUID sessionId, UUID campaignId, List<CommittedEntityVersion> committedVersions) {}
