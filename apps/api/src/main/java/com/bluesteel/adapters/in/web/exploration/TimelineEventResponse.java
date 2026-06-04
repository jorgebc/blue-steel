package com.bluesteel.adapters.in.web.exploration;

import com.bluesteel.application.model.worldstate.TimelineEntryView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Wire DTO for one Timeline feed entry (F4.2.4). Projects the read model's event identity, snapshot
 * fields, and originating session reference; the raw {@code full_snapshot} is intentionally omitted
 * from the feed (event detail is reached via click-through to the entity endpoint).
 */
public record TimelineEventResponse(
    UUID eventId,
    String name,
    String eventType,
    List<String> involvedActorNames,
    String spaceName,
    UUID sessionId,
    Integer sessionSequenceNumber,
    Instant createdAt) {

  public static TimelineEventResponse from(TimelineEntryView v) {
    return new TimelineEventResponse(
        v.eventId(),
        v.name(),
        v.eventType(),
        v.involvedActorNames(),
        v.spaceName(),
        v.sessionId(),
        v.sessionSequenceNumber(),
        v.createdAt());
  }
}
