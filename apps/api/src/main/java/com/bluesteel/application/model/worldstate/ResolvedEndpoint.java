package com.bluesteel.application.model.worldstate;

import java.util.UUID;

/**
 * A relation endpoint resolved to a concrete world-state entity: the entity's id plus its type
 * ({@code actor} or {@code space}). Returned by best-effort name matching at commit (F4.3.4,
 * D-095).
 */
public record ResolvedEndpoint(UUID entityId, String entityType) {}
