package com.bluesteel.application.model.worldstate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-side projection of one event for the Timeline feed (F4.2): the event's latest committed
 * version plus the session it belongs to. {@code eventType}, {@code involvedActorNames}, and {@code
 * spaceName} are read from the event's structured relational links (the {@code events.event_type} /
 * {@code events.space_id} columns and the {@code event_involved_actors} join table) populated at
 * commit (F4.6, D-097); they may be null/empty when the extractor could not identify them.
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
