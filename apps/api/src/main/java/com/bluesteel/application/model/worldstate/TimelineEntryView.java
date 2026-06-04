package com.bluesteel.application.model.worldstate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-side projection of one event for the Timeline feed (F4.2): the event's latest committed
 * version plus the session it belongs to. {@code eventType}, {@code involvedActorNames}, and {@code
 * spaceName} are read from {@code full_snapshot} and may be absent (null / empty) until the
 * extraction pipeline enriches event snapshots.
 */
public record TimelineEntryView(
    UUID eventId,
    String name,
    String eventType,
    List<String> involvedActorNames,
    String spaceName,
    UUID sessionId,
    Integer sessionSequenceNumber,
    Map<String, Object> fullSnapshot,
    Instant createdAt) {}
