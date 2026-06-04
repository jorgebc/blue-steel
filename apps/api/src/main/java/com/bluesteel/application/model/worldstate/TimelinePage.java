package com.bluesteel.application.model.worldstate;

import java.util.List;

/**
 * One keyset-paginated page of the Timeline feed (D-055, F4.2). {@code nextCursor} is the opaque
 * cursor of the last entry, or {@code null} when no further pages exist.
 */
public record TimelinePage(List<TimelineEntryView> events, String nextCursor) {}
