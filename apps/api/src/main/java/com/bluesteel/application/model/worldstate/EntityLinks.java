package com.bluesteel.application.model.worldstate;

import com.bluesteel.application.model.session.SessionSummaryView;
import java.util.List;

/**
 * Cross-link bundle for one world-state entity's profile (F4.7, D-009): the relations it
 * participates in, the distinct entities at the other end of those relations, the events linked to
 * it (events occurring in a space / involving an actor, D-095), and the sessions it appears in.
 * Appearances carry the session summary (sequence number, status) so the UI can label and deep-link
 * them (F4.8.1).
 */
public record EntityLinks(
    List<RelationSummaryView> relations,
    List<EntitySummaryView> relatedEntities,
    List<TimelineEntryView> events,
    List<SessionSummaryView> appearances) {}
