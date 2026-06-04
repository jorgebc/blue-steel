package com.bluesteel.application.model.worldstate;

/**
 * Optional, all-nullable filters for the Timeline feed (F4.2). {@code eventType} matches the
 * event's {@code full_snapshot.eventType} exactly; {@code actor} and {@code space} are
 * case-insensitive substring matches against the snapshot's involved actors and space respectively.
 */
public record TimelineFilter(String actor, String space, String eventType) {

  /** A filter that constrains nothing. */
  public static TimelineFilter none() {
    return new TimelineFilter(null, null, null);
  }
}
