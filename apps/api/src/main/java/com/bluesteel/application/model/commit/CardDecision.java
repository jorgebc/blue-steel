package com.bluesteel.application.model.commit;

import java.util.Map;
import java.util.UUID;

/**
 * User decision for a single diff card. {@code editedFields} is non-null only when {@code action ==
 * EDIT} and carries the fields the user modified.
 */
public record CardDecision(UUID cardId, CardAction action, Map<String, Object> editedFields) {}
