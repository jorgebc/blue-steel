package com.bluesteel.adapters.in.web.exploration;

import com.bluesteel.adapters.in.web.ApiResponse;
import com.bluesteel.application.model.worldstate.TimelineFilter;
import com.bluesteel.application.model.worldstate.TimelinePage;
import com.bluesteel.application.port.in.worldstate.GetTimelineUseCase;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only Timeline feed endpoint for Exploration Mode (D-009, D-010). Returns a campaign's events
 * across all committed sessions with keyset (cursor) pagination (D-055): {@code data { events }}
 * and {@code meta { nextCursor }}, where a null {@code nextCursor} signals the end of the feed.
 */
@RestController
@RequestMapping("/api/v1/campaigns/{id}")
public class TimelineController {

  private final GetTimelineUseCase getTimelineUseCase;

  public TimelineController(GetTimelineUseCase getTimelineUseCase) {
    this.getTimelineUseCase = getTimelineUseCase;
  }

  @GetMapping("/timeline")
  public ResponseEntity<ApiResponse<TimelineDataResponse>> getTimeline(
      @PathVariable UUID id,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(required = false) String actor,
      @RequestParam(required = false) String space,
      @RequestParam(required = false) String eventType) {
    UUID callerId = resolveCallerId();
    TimelinePage page =
        getTimelineUseCase.getTimeline(
            id, callerId, cursor, limit, new TimelineFilter(actor, space, eventType));

    List<TimelineEventResponse> events =
        page.events().stream().map(TimelineEventResponse::from).toList();
    // singletonMap (not Map.of) so nextCursor can be a null value on the final page.
    return ResponseEntity.ok(
        ApiResponse.success(
            new TimelineDataResponse(events),
            Collections.singletonMap("nextCursor", page.nextCursor())));
  }

  private UUID resolveCallerId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return UUID.fromString(auth.getName());
  }

  /** Wraps the feed entries so the envelope's {@code data} is {@code { events: [...] }}. */
  public record TimelineDataResponse(List<TimelineEventResponse> events) {}
}
