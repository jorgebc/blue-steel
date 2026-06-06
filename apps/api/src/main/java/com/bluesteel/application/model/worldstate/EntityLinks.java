package com.bluesteel.application.model.worldstate;

import java.util.List;
import java.util.UUID;

/**
 * Cross-link bundle for one world-state entity's profile (F4.7, D-009): the relations it
 * participates in, the distinct entities at the other end of those relations, the events linked to
 * it (events occurring in a space / involving an actor, D-095), and the sessions it appears in.
 */
public record EntityLinks(
    List<RelationSummaryView> relations,
    List<EntitySummaryView> relatedEntities,
    List<TimelineEntryView> events,
    List<UUID> appearanceSessionIds) {}
