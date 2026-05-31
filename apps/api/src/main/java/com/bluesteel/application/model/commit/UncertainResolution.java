package com.bluesteel.application.model.commit;

import java.util.UUID;

/**
 * User resolution for an UNCERTAIN entity card (D-042). {@code matchedEntityId} is non-null only
 * when {@code resolution == MATCH}.
 */
public record UncertainResolution(UUID cardId, ResolutionType resolution, UUID matchedEntityId) {}
