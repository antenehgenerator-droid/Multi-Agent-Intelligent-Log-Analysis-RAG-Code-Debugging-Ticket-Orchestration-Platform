package com.platform.rag.retriever;

import com.pgvector.PGvector;
import com.platform.rag.model.RetrievalQuery;
import com.platform.rag.model.RetrievedChunk;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class HybridRetriever implements Retriever {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public HybridRetriever(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public List<RetrievedChunk> retrieve(RetrievalQuery query) {
    CorpusView view = CorpusView.forCorpus(query.corpus());

    MapSqlParameterSource params = new MapSqlParameterSource();
    params.addValue("query_text", query.text());
    params.addValue("query_vec", new PGvector(query.vector()));
    params.addValue("limit_bound", query.topK() * 5);
    params.addValue("final_limit", query.topK());

    String filterSql = buildFilterSql(view, query.filters(), params);

    String sql =
        """
        WITH vec AS (
            SELECT id, %s AS content,
                   ROW_NUMBER() OVER (ORDER BY embedding <=> :query_vec) AS rnk
            FROM %s
            WHERE 1=1 %s
            ORDER BY embedding <=> :query_vec
            LIMIT :limit_bound
        ),
        lex AS (
            SELECT id, %s AS content,
                   ROW_NUMBER() OVER (ORDER BY similarity(%s, :query_text) DESC) AS rnk
            FROM %s
            WHERE %s %% :query_text %s
            ORDER BY similarity(%s, :query_text) DESC
            LIMIT :limit_bound
        ),
        fused AS (
            SELECT COALESCE(v.id, l.id) AS id,
                   (COALESCE(1.0 / (60 + v.rnk), 0.0) + COALESCE(1.0 / (60 + l.rnk), 0.0)) AS rrf_score
            FROM vec v
            FULL OUTER JOIN lex l USING (id)
        )
        SELECT f.rrf_score, t.id, %s AS content
        FROM fused f
        JOIN %s t ON t.id = f.id
        ORDER BY f.rrf_score DESC
        LIMIT :final_limit
        """
            .formatted(
                view.contentExpression(),
                view.table(),
                filterSql,
                view.contentExpression(),
                view.contentExpression(),
                view.table(),
                view.contentExpression(),
                filterSql,
                view.contentExpression(),
                view.contentExpression(),
                view.table());

    return jdbcTemplate.query(
        sql,
        params,
        (rs, rowNum) ->
            new RetrievedChunk(
                rs.getString("id"),
                rs.getString("content"),
                rs.getDouble("rrf_score"),
                view.metadataFromRow(rs)));
  }

  private static String buildFilterSql(
      CorpusView view, Map<String, Object> filters, MapSqlParameterSource params) {
    if (filters == null || filters.isEmpty()) {
      return "";
    }
    StringBuilder clauses = new StringBuilder();
    int index = 0;
    for (Map.Entry<String, Object> entry : filters.entrySet()) {
      String column = view.filterColumns().get(entry.getKey());
      if (column == null || entry.getValue() == null) {
        continue;
      }
      String param = "filter_" + index++;
      clauses.append(" AND ").append(column).append(" = :").append(param);
      params.addValue(param, entry.getValue().toString());
    }
    return clauses.toString();
  }

  private record CorpusView(String table, String contentExpression, Map<String, String> filterColumns) {

    static CorpusView forCorpus(String corpus) {
      return switch (corpus.toUpperCase()) {
        case "LOGS" ->
            new CorpusView(
                "log_embeddings",
                "content",
                Map.of("fingerprint", "fingerprint", "service", "content"));
        case "CODE" ->
            new CorpusView(
                "code_embeddings",
                "COALESCE(content, file_path || ' ' || fqn)",
                Map.of("repo", "repo", "git_sha", "git_sha", "file_path", "file_path", "fqn", "fqn"));
        case "INCIDENTS" ->
            new CorpusView("incident_embeddings", "content", Map.of("incident_id", "incident_id"));
        default -> throw new IllegalArgumentException("Unknown corpus source designation: " + corpus);
      };
    }

    Map<String, Object> metadataFromRow(ResultSet rs) throws SQLException {
      Map<String, Object> metadata = new HashMap<>();
      metadata.put("corpus", table);
      metadata.put("id", rs.getString("id"));
      return metadata;
    }
  }
}
