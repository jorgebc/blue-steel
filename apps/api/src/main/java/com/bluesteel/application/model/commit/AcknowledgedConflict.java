package com.bluesteel.application.model.commit;

import java.util.UUID;

/** User acknowledgement of a detected conflict card (D-033). */
public record AcknowledgedConflict(UUID conflictId) {}
