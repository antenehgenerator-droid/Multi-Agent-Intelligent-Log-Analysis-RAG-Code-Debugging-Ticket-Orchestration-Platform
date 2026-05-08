package com.platform.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.core.util.Fingerprinter;
import com.platform.core.util.LogNormalizer;
import com.platform.core.util.LogParser;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class PreprocessingTest {

  @Test
  void testRealisticSamples() {
    String log =
        "2026-05-08 14:00:00 ERROR [main] c.p.Service : Connection failed to 192.168.1.50:5432 after 3000ms";
    String logSame =
        "2026-05-08 14:05:00 ERROR [worker-1] c.p.Service : Connection failed to 10.0.0.1:5432 after 1500ms";

    String f1 = getFingerprint(log);
    String f2 = getFingerprint(logSame);

    assertThat(f1).isEqualTo(f2);
  }

  private static String getFingerprint(String rawLog) {
    LogParser parser = new LogParser();
    LogNormalizer normalizer = new LogNormalizer();
    Fingerprinter fingerprinter = new Fingerprinter();

    Map<String, Object> structured = parser.parse(rawLog);
    String body = structured.getOrDefault("message", rawLog).toString();
    String template = normalizer.normalize(body);
    return fingerprinter.compute("svc", "ERROR", template);
  }
}

