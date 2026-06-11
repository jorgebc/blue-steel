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
 * env-overridable ({@code query.rate-limit.*}); the per-key deque is bounded by {@code maxRequests}
 * and empty keys are pruned, so the map is bounded by the number of keys active within the window.
 */
@Component
public class QueryRateLimiter {

  private static final Logger log = LoggerFactory.getLogger(QueryRateLimiter.class);

  private final int maxRequests;
  private final Duration window;
  private final Clock clock;
  private final ConcurrentHashMap<Key, Deque<Instant>> hits = new ConcurrentHashMap<>();

  @Autowired
  public QueryRateLimiter(
      @Value("${query.rate-limit.max-requests:10}") int maxRequests,
      @Value("${query.rate-limit.window-seconds:60}") long windowSeconds) {
    this(maxRequests, windowSeconds, Clock.systemUTC());
  }

  QueryRateLimiter(int maxRequests, long windowSeconds, Clock clock) {
    this.maxRequests = maxRequests;
    this.window = Duration.ofSeconds(windowSeconds);
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
  }

  private record Key(UUID userId, UUID campaignId) {}
}
