package com.platform.queue.config;

import java.util.concurrent.TimeUnit;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;

/**
 * Back-off intervals between retry attempts: 30s, 2m, 8m, 30m, 2h. After the last interval,
 * {@link BackOffExecution#STOP} is returned so the recoverer (DLQ) runs.
 */
public final class SequencedBackOff implements BackOff {

  private static final long[] INTERVAL_MS = {
    TimeUnit.SECONDS.toMillis(30),
    TimeUnit.MINUTES.toMillis(2),
    TimeUnit.MINUTES.toMillis(8),
    TimeUnit.MINUTES.toMillis(30),
    TimeUnit.HOURS.toMillis(2)
  };

  @Override
  public BackOffExecution start() {
    return new BackOffExecution() {
      private int next;

      @Override
      public long nextBackOff() {
        if (next >= INTERVAL_MS.length) {
          return STOP;
        }
        return INTERVAL_MS[next++];
      }
    };
  }
}
