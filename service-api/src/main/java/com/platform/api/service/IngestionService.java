package com.platform.api.service;

import com.platform.core.model.LogIngestionRequest.LogEntry;
import com.platform.core.util.LogNormalizer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestionService {
  private final SimpleJdbcInsert jdbcInsert;
  private final LogNormalizer normalizer;

  public IngestionService(DataSource dataSource, LogNormalizer normalizer) {
    this.jdbcInsert =
        new SimpleJdbcInsert(dataSource)
            .withTableName("log_events")
            .usingColumns("service", "level", "message", "fingerprint", "occurred_at");
    this.normalizer = normalizer;
  }

  @Transactional
  public void ingest(List<LogEntry> entries) {
    // Explicitly define the stream type to help the compiler
    List<Map<String, Object>> batch =
        entries.stream()
            .<Map<String, Object>>map(
                e -> {
                  String norm = normalizer.normalize(e.message());
                  Map<String, Object> row = new java.util.HashMap<>();
                  row.put("service", e.service());
                  row.put("level", e.level());
                  row.put("message", e.message());
                  row.put("fingerprint", computeFingerprint(e.service(), e.level(), norm));
                  row.put("occurred_at", java.sql.Timestamp.from(e.occurredAt()));
                  return row;
                })
            .toList(); // This requires Java 16+
    @SuppressWarnings("unchecked")
    Map<String, Object>[] batchArray = batch.toArray(new Map[0]);
    jdbcInsert.executeBatch(batchArray);
  }

  private String computeFingerprint(String s, String l, String m) {
    try {
      String input = s + l + m;
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Hashing failed", e);
    }
  }
}
