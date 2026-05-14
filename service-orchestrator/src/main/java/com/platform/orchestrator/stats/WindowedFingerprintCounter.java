package com.platform.orchestrator.stats;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Per-5-minute-window frequency using Redis sorted sets.
 *
 * <p><b>ZINCRBY</b>: each window key {@code stats:freq:w:{windowId}} holds members {@code
 * service:fingerprint} with score = event count in that window (standard Redis counter-in-ZSET
 * pattern).
 *
 * <p><b>ZRANGEBYSCORE</b>: {@link #fingerprintsInCountRange(long, double, double)} queries one
 * window bucket for members whose scores lie in {@code [minCount, maxCount]} (useful for slicing a
 * busy window by volume).
 *
 * <p>24h rolling baseline for a single (service, fingerprint) is built by aggregating per-window
 * scores across past window keys (excluding the current bucket).
 */
@Component
public class WindowedFingerprintCounter {

  private static final String WINDOW_KEY_PREFIX = "stats:freq:w:";
  private static final String SERVICE_ERR_WINDOW_PREFIX = "stats:svc:err:w:";
  private static final long WINDOW_SECONDS = 300;

  private final RedisTemplate<String, String> redis;

  public WindowedFingerprintCounter(RedisTemplate<String, String> redis) {
    this.redis = redis;
  }

  /** ZINCRBY on the current 5-minute window key for (service, fingerprint). */
  public void increment(String service, String fingerprint, Instant timestamp) {
    long window = windowOf(timestamp);
    String key = windowKey(window);
    String member = member(service, fingerprint);
    redis.opsForZSet().incrementScore(key, member, 1.0);
    redis.expire(key, Duration.ofHours(26));
  }

  /** Count ERROR-level lines per service in the current 5-minute window (for rate-jump signal). */
  public void incrementServiceError(String service, Instant timestamp) {
    long window = windowOf(timestamp);
    String key = serviceErrWindowKey(window);
    redis.opsForZSet().incrementScore(key, service, 1.0);
    redis.expire(key, Duration.ofHours(26));
  }

  public double getCount(String service, String fingerprint, Instant timestamp) {
    long window = windowOf(timestamp);
    Double score = redis.opsForZSet().score(windowKey(window), member(service, fingerprint));
    return score == null ? 0.0 : score;
  }

  public double getServiceErrorCount(String service, Instant timestamp) {
    long window = windowOf(timestamp);
    Double score = redis.opsForZSet().score(serviceErrWindowKey(window), service);
    return score == null ? 0.0 : score;
  }

  /**
   * ZRANGEBYSCORE on a single 5-minute window: members whose scores are in {@code [minScore,
   * maxScore]} (inclusive).
   */
  public Set<String> fingerprintsInCountRange(long windowId, double minScore, double maxScore) {
    return redis.opsForZSet().rangeByScore(windowKey(windowId), minScore, maxScore);
  }

  public DescriptiveStatistics fingerprintBaseline(
      String service, String fingerprint, Instant timestamp, int pastWindows) {
    long current = windowOf(timestamp);
    DescriptiveStatistics stats = new DescriptiveStatistics();
    for (int i = 1; i <= pastWindows; i++) {
      long w = current - i;
      Double score = redis.opsForZSet().score(windowKey(w), member(service, fingerprint));
      stats.addValue(score == null ? 0.0 : score);
    }
    return stats;
  }

  public DescriptiveStatistics serviceErrorBaseline(String service, Instant timestamp, int pastWindows) {
    long current = windowOf(timestamp);
    DescriptiveStatistics stats = new DescriptiveStatistics();
    for (int i = 1; i <= pastWindows; i++) {
      long w = current - i;
      Double score = redis.opsForZSet().score(serviceErrWindowKey(w), service);
      stats.addValue(score == null ? 0.0 : score);
    }
    return stats;
  }

  public static long windowOf(Instant timestamp) {
    Instant ts = timestamp == null ? Instant.now() : timestamp;
    return ts.getEpochSecond() / WINDOW_SECONDS;
  }

  private static String windowKey(long window) {
    return WINDOW_KEY_PREFIX + window;
  }

  private static String serviceErrWindowKey(long window) {
    return SERVICE_ERR_WINDOW_PREFIX + window;
  }

  private static String member(String service, String fingerprint) {
    return service + ":" + fingerprint;
  }
}
