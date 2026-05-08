package com.platform.orchestrator.stats;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class WindowedFingerprintCounter {
  private static final String KEY_PREFIX = "stats:freq:";
  private static final long WINDOW_SECONDS = 300; // 5 minutes

  private final RedisTemplate<String, String> redis;

  public WindowedFingerprintCounter(RedisTemplate<String, String> redis) {
    this.redis = redis;
  }

  public void increment(String fingerprint, Instant timestamp) {
    long window = windowOf(timestamp);
    String key = keyForWindow(window);
    redis.opsForZSet().incrementScore(key, fingerprint, 1.0);
    redis.expire(key, Duration.ofHours(24));
  }

  public double getCount(String fingerprint, Instant timestamp) {
    long window = windowOf(timestamp);
    Double score = redis.opsForZSet().score(keyForWindow(window), fingerprint);
    return score == null ? 0.0 : score;
  }

  /**
   * Returns baseline statistics built from the last {@code windowsBack} windows (excluding the
   * current window).
   */
  public DescriptiveStatistics baseline(String fingerprint, Instant timestamp, int windowsBack) {
    long currentWindow = windowOf(timestamp);
    DescriptiveStatistics stats = new DescriptiveStatistics();
    for (int i = 1; i <= windowsBack; i++) {
      long w = currentWindow - i;
      Double score = redis.opsForZSet().score(keyForWindow(w), fingerprint);
      stats.addValue(score == null ? 0.0 : score);
    }
    return stats;
  }

  /**
   * Returns baseline statistics for the provided explicit window ids (unix-epoch bucket ids), where
   * each bucket is 5 minutes.
   */
  public DescriptiveStatistics baseline(String fingerprint, List<Long> windows) {
    DescriptiveStatistics stats = new DescriptiveStatistics();
    for (Long w : windows) {
      if (w == null) continue;
      Double score = redis.opsForZSet().score(keyForWindow(w), fingerprint);
      stats.addValue(score == null ? 0.0 : score);
    }
    return stats;
  }

  private static long windowOf(Instant timestamp) {
    Instant ts = timestamp == null ? Instant.now() : timestamp;
    return ts.getEpochSecond() / WINDOW_SECONDS;
  }

  private static String keyForWindow(long window) {
    return KEY_PREFIX + window;
  }
}

