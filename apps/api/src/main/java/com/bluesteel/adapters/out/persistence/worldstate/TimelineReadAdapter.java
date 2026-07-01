package com.bluesteel.adapters.out.persistence.worldstate;

import com.bluesteel.application.model.worldstate.TimelineEntryView;
import com.bluesteel.application.model.worldstate.TimelineFilter;
import com.bluesteel.application.model.worldstate.TimelinePage;
import com.bluesteel.application.port.out.worldstate.TimelineReadPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Native-SQL implementation of {@link TimelineReadPort} (D-062, D-089). Builds the event feed from
 * each event's latest committed version, ordered by {@code (session.sequence_number, event.id)} so
 * the keyset cursor is total and gap-free. Event type, space, and involved actors are read from the
 * event's structured relational links (the {@code events.event_type} / {@code events.space_id}
 * columns and the {@code event_involved_actors} join table) populated at commit (F4.6, D-097); the
 * latest version's {@code full_snapshot} is still surfaced as the raw snapshot map.
 */
@Component
public class TimelineReadAdapter implements TimelineReadPort {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public TimelineReadAdapter(@Lazy JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  private record Cursor(int sequenceNumber, UUID eventId) {}

  @Override
  public TimelinePage page(UUID campaignId, String cursor, int limit, TimelineFilter filter) {
    Cursor decoded = cursor == null ? null : decodeCursor(cursor);
    TimelineFilter safeFilter = filter == null ? TimelineFilter.none() : filter;

    StringBuilder sql =
        new StringBuilder(
            """
            SELECT e.id AS event_id,
                   e.name,
                   e.event_type,
                   e.created_at,
                   sp.name AS space_name,
                   ev.full_snapshot::text AS full_snapshot,
                   ev.session_id,
                   s.sequence_number AS session_seq,
                   (SELECT array_agg(a.name ORDER BY a.name)
                      FROM event_involved_actors ea
                      JOIN actors a ON a.id = ea.actor_id
                     WHERE ea.event_id = e.id) AS involved_actors
            FROM events e
            JOIN event_versions ev ON ev.event_id = e.id
            JOIN sessions s ON s.id = ev.session_id
            LEFT JOIN spaces sp ON sp.id = e.space_id
            WHERE e.campaign_id = ?
              AND s.status = 'committed'
              AND ev.version_number = (
                  SELECT MAX(v2.version_number) FROM event_versions v2 WHERE v2.event_id = e.id)
            """);

    List<Object> args = new ArrayList<>();
    args.add(campaignId);

    if (decoded != null) {
      sql.append(" AND (s.sequence_number, e.id) > (?, ?)\n");
      args.add(decoded.sequenceNumber());
      args.add(decoded.eventId());
    }
    if (safeFilter.eventType() != null) {
      sql.append(" AND e.event_type ILIKE ?\n");
      args.add("%" + safeFilter.eventType() + "%");
    }
    if (safeFilter.space() != null) {
      sql.append(" AND sp.name ILIKE ?\n");
      args.add("%" + safeFilter.space() + "%");
    }
    if (safeFilter.actor() != null) {
      sql.append(
          " AND EXISTS (SELECT 1 FROM event_involved_actors ea JOIN actors a ON a.id = ea.actor_id"
              + " WHERE ea.event_id = e.id AND a.name ILIKE ?)\n");
      args.add("%" + safeFilter.actor() + "%");
    }

    sql.append(" ORDER BY s.sequence_number ASC, e.id ASC\n LIMIT ?");
    args.add(limit);

    List<TimelineEntryView> events =
        jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapEntry(rs), args.toArray());

    String nextCursor = null;
    if (events.size() == limit && !events.isEmpty()) {
      TimelineEntryView last = events.get(events.size() - 1);
      nextCursor = encodeCursor(new Cursor(last.sessionSequenceNumber(), last.eventId()));
    }

    return new TimelinePage(events, nextCursor);
  }

  private TimelineEntryView mapEntry(ResultSet rs) throws SQLException {
    Map<String, Object> snapshot = parseJson(rs.getString("full_snapshot"));
    return new TimelineEntryView(
        rs.getObject("event_id", UUID.class),
        rs.getString("name"),
        rs.getString("event_type"),
        involvedActorNames(rs.getArray("involved_actors")),
        rs.getString("space_name"),
        rs.getObject("session_id", UUID.class),
        rs.getObject("session_seq", Integer.class),
        snapshot,
        instant(rs, "created_at"));
  }

  private static List<String> involvedActorNames(java.sql.Array array) throws SQLException {
    if (array == null) {
      return List.of();
    }
    String[] names = (String[]) array.getArray();
    return java.util.Arrays.stream(names).filter(java.util.Objects::nonNull).toList();
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }

  private Map<String, Object> parseJson(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to parse event snapshot JSONB", e);
    }
  }

  private static String encodeCursor(Cursor cursor) {
    String raw = cursor.sequenceNumber() + ":" + cursor.eventId();
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  private static Cursor decodeCursor(String cursor) {
    try {
      String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      int separator = raw.indexOf(':');
      if (separator < 0) {
        throw new IllegalArgumentException("Malformed timeline cursor");
      }
      int sequenceNumber = Integer.parseInt(raw.substring(0, separator));
      UUID eventId = UUID.fromString(raw.substring(separator + 1));
      return new Cursor(sequenceNumber, eventId);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Malformed timeline cursor", e);
    }
  }
}
