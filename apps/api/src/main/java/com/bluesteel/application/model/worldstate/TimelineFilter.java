package com.bluesteel.application.model.worldstate;

/**
 * Optional, all-nullable filters for the Timeline feed (F4.2). All three are case-insensitive
 * substring matches: {@code eventType} against the event's {@code event_type}, {@code actor}
 * against the involved actors' names, and {@code space} against the space name.
 */
public record TimelineFilter(String actor, String space, String eventType) {

  /** A filter that constrains nothing. */
  public static TimelineFilter none() {
    return new TimelineFilter(null, null, null);
  }
}
