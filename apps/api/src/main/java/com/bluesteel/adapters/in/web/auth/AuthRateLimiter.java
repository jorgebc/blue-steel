package com.bluesteel.adapters.in.web.auth;

import com.bluesteel.domain.exception.AuthRateLimitExceededException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * In-memory sliding-window rate limiter for the auth endpoints (login/refresh), keyed by client IP.
 * Each call records a timestamp; an IP whose window holds {@code maxRequests} timestamps rejects
 * the next request with {@link AuthRateLimitExceededException}. The window/limit are
 * env-overridable ({@code auth.rate-limit.*}); the per-key deque is bounded by {@code maxRequests}.
 * Once the map grows past {@code auth.rate-limit.max-tracked-keys}, every check also prunes keys
 * whose newest timestamp has left the window (stale keys can never reject again, so removal is
 * safe; active keys are never evicted — that would reset their count and bypass the limit). The map
 * is therefore bounded by {@code max(maxTrackedKeys, keys active within one window)}.
 *
 * <p>This mirrors {@link com.bluesteel.adapters.in.web.query.QueryRateLimiter}. It is best-effort
 * availability protection (the IP comes from a spoofable {@code X-Forwarded-For} header set by the
 * reverse proxy), not an authentication control, and is per-instance (state resets on redeploy).
 */
@Component
public class AuthRateLimiter {

  private static final Logger log = LoggerFactory.getLogger(AuthRateLimiter.class);

  private final int maxRequests;
  private final Duration window;
  private final int maxTrackedKeys;
  private final Clock clock;
  private final ConcurrentHashMap<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

  @Autowired
  public AuthRateLimiter(
      @Value("${auth.rate-limit.max-requests:10}") int maxRequests,
      @Value("${auth.rate-limit.window-seconds:60}") long windowSeconds,
      @Value("${auth.rate-limit.max-tracked-keys:1000}") int maxTrackedKeys) {
    this(maxRequests, windowSeconds, maxTrackedKeys, Clock.systemUTC());
  }

  AuthRateLimiter(int maxRequests, long windowSeconds, int maxTrackedKeys, Clock clock) {
    this.maxRequests = maxRequests;
    this.window = Duration.ofSeconds(windowSeconds);
    this.maxTrackedKeys = maxTrackedKeys;
    this.clock = clock;
  }

  /**
   * Records an auth attempt for the client IP and throws {@link AuthRateLimitExceededException} if
   * it would exceed the configured rate within the sliding window.
   */
  public void check(String clientIp) {
    Instant now = clock.instant();
    Instant cutoff = now.minus(window);

    hits.compute(
        clientIp,
        (k, timestamps) -> {
          Deque<Instant> recent = timestamps != null ? timestamps : new ArrayDeque<>();
          while (!recent.isEmpty() && recent.peekFirst().isBefore(cutoff)) {
            recent.pollFirst();
          }
          if (recent.size() >= maxRequests) {
            log.warn(
                "Auth rate limit tripped clientIp={} maxRequests={} windowSeconds={}",
                k,
                maxRequests,
                window.toSeconds());
            throw new AuthRateLimitExceededException();
          }
          recent.addLast(now);
          return recent;
        });

    if (hits.size() > maxTrackedKeys) {
      pruneStaleKeys(cutoff);
    }
  }

  /**
   * Removes keys whose newest timestamp predates the window. Per-key removal goes through {@code
   * computeIfPresent} so it cannot race a concurrent {@link #check} on the same key.
   */
  private void pruneStaleKeys(Instant cutoff) {
    for (String key : hits.keySet()) {
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
}
