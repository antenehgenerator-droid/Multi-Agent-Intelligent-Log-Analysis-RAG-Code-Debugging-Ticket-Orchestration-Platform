package com.platform.orchestrator.stats;

import java.time.Duration;
import java.time.Instant;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Tracks per-(service, fingerprint) metadata for rolling baselines and cold-start.
 *
 * <p>24h rolling frequency samples are read from {@link WindowedFingerprintCounter} (Redis ZSETs per
 * 5-minute window). This store holds auxiliary HASH state keyed by {@code stats:baseline:v1:...}.
 */
@Component
public class BaselineStore {

  private static final String HASH_PREFIX = "stats:baseline:v1:";
  private static final Duration COLD_START_PERIOD = Duration.ofHours(24);

  private final RedisTemplate<String, String> redis;

  public BaselineStore(RedisTemplate<String, String> redis) {
    this.redis = redis;
  }

  /** Records first observation time for (service, fingerprint) if not yet present. */
  public void ensureFirstSeen(String service, String fingerprint, Instant now) {
    String key = hashKey(service, fingerprint);
    redis.opsForHash().putIfAbsent(key, "first_seen_epoch_sec", Long.toString(now.getEpochSecond()));
    redis.expire(key, Duration.ofDays(2));
  }

  /**
   * Cold-start: for the first 24 wall-clock hours after a fingerprint is first seen for a service,
   * use fixed default thresholds in {@link com.platform.orchestrator.detector.AnomalyDetector}
   * instead of z-score / rate statistics that lack sufficient history.
   */
  public boolean isColdStart(String service, String fingerprint, Instant now) {
    Object raw = redis.opsForHash().get(hashKey(service, fingerprint), "first_seen_epoch_sec");
    if (raw == null) {
      return true;
    }
    long first;
    try {
      first = Long.parseLong(raw.toString());
    } catch (NumberFormatException e) {
      return true;
    }
    return now.getEpochSecond() - first < COLD_START_PERIOD.toSeconds();
  }

  private static String hashKey(String service, String fingerprint) {
    return HASH_PREFIX + service + ":" + fingerprint;
  }
}
