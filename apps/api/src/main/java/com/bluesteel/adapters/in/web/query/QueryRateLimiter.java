package com.bluesteel.adapters.in.web.query;

import com.bluesteel.domain.exception.RateLimitExceededException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * In-memory sliding-window rate limiter for Query Mode, keyed by {@code (userId, campaignId)}
 * (D-096). Each call records a timestamp; a key whose window holds {@code maxRequests} timestamps
 * rejects the next request with {@link RateLimitExceededException}. The window/limit are
 * env-overridable ({@code query.rate-limit.*}); the per-key deque is bounded by {@code
 * maxRequests}. Once the map grows past {@code query.rate-limit.max-tracked-keys}, every check also
 * prunes keys whose newest timestamp has left the window (stale keys can never reject again, so
 * removal is safe; active keys are never evicted — that would reset their count and bypass the
 * limit). The map is therefore bounded by {@code max(maxTrackedKeys, keys active within one
 * window)}.
 */
@Component
public class QueryRateLimiter {

  private static final Logger log = LoggerFactory.getLogger(QueryRateLimiter.class);

  private final int maxRequests;
  private final Duration window;
  private final int maxTrackedKeys;
  private final Clock clock;
  private final ConcurrentHashMap<Key, Deque<Instant>> hits = new ConcurrentHashMap<>();

  @Autowired
  public QueryRateLimiter(
      @Value("${query.rate-limit.max-requests:10}") int maxRequests,
      @Value("${query.rate-limit.window-seconds:60}") long windowSeconds,
      @Value("${query.rate-limit.max-tracked-keys:1000}") int maxTrackedKeys) {
    this(maxRequests, windowSeconds, maxTrackedKeys, Clock.systemUTC());
  }

  QueryRateLimiter(int maxRequests, long windowSeconds, int maxTrackedKeys, Clock clock) {
    this.maxRequests = maxRequests;
    this.window = Duration.ofSeconds(windowSeconds);
    this.maxTrackedKeys = maxTrackedKeys;
    this.clock = clock;
  }

  /**
   * Records a query for the caller and throws {@link RateLimitExceededException} if it would exceed
   * the configured rate within the sliding window.
   */
  public void check(UUID userId, UUID campaignId) {
    Key key = new Key(userId, campaignId);
    Instant now = clock.instant();
    Instant cutoff = now.minus(window);

    hits.compute(
        key,
        (k, timestamps) -> {
          Deque<Instant> recent = timestamps != null ? timestamps : new ArrayDeque<>();
          while (!recent.isEmpty() && recent.peekFirst().isBefore(cutoff)) {
            recent.pollFirst();
          }
          if (recent.size() >= maxRequests) {
            log.warn(
                "Query rate limit tripped userId={} campaignId={} maxRequests={} windowSeconds={}",
                k.userId(),
                k.campaignId(),
                maxRequests,
                window.toSeconds());
            throw new RateLimitExceededException();
          }
          recent.addLast(now);
          return recent;
        });

    if (hits.size() > maxTrackedKeys) {
      pruneStaleKeys(cutoff);
    }
  }

  /**
   * Returns how many more requests the caller may make in the current window, without recording a
   * hit — querying remaining capacity must never consume it. Prunes the key's stale timestamps in
   * passing (consistent with {@link #check}); never adds one.
   */
  public int remaining(UUID userId, UUID campaignId) {
    Key key = new Key(userId, campaignId);
    Instant cutoff = clock.instant().minus(window);
    Deque<Instant> recent =
        hits.computeIfPresent(
            key,
            (k, timestamps) -> {
              while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                timestamps.pollFirst();
              }
              return timestamps;
            });
    int used = recent == null ? 0 : recent.size();
    return Math.max(0, maxRequests - used);
  }

  public int maxRequests() {
    return maxRequests;
  }

  public long windowSeconds() {
    return window.toSeconds();
  }

  /**
   * Removes keys whose newest timestamp predates the window. Per-key removal goes through {@code
   * computeIfPresent} so it cannot race a concurrent {@link #check} on the same key.
   */
  private void pruneStaleKeys(Instant cutoff) {
    for (Key key : hits.keySet()) {
      hits.computeIfPresent(
          key,
          (k, timestamps) -> {
            Instant newest = timestamps.peekLast();
            return newest != null && newest.isBefore(cutoff) ? null : timestamps;
          });
    }
  }

  int trackedKeyCount() {
    return hits.size();
  }

  private record Key(UUID userId, UUID campaignId) {}
}
