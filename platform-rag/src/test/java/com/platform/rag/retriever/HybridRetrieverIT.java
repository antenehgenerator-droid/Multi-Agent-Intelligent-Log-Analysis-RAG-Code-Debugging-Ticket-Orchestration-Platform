package com.platform.rag.retriever;

import static org.assertj.core.api.Assertions.assertThat;

import com.pgvector.PGvector;
import com.platform.rag.RagRetrieverTestApplication;
import com.platform.rag.model.RetrievalQuery;
import com.platform.rag.model.RetrievedChunk;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = RagRetrieverTestApplication.class)
@Testcontainers
class HybridRetrieverIT {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("pgvector/pgvector:pg16");

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired private HybridRetriever retriever;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @BeforeEach
  void seedDatabaseFixtures() {
    jdbcTemplate.getJdbcTemplate().execute("DELETE FROM code_embeddings");

    insertFixture(
        "1",
        "public void processPayment() { throw new NullPointerException(\"E_PAY_42\"); }",
        new float[1536]);
    insertFixture(
        "2",
        "public void processPaymentWithRetry() { /* fallback connection pool trace */ }",
        new float[1536]);
  }

  private void insertFixture(String suffix, String content, float[] vector) {
    jdbcTemplate.update(
        """
        INSERT INTO code_embeddings (repo, git_sha, file_path, fqn, content, embedding)
        VALUES ('repo', 'sha', 'PaymentService.java', :fqn, :content, :embedding)
        """,
        new MapSqlParameterSource()
            .addValue("fqn", "com.example#pay" + suffix)
            .addValue("content", content)
            .addValue("embedding", new PGvector(vector)));
  }

  @Test
  void retrieve_combinesVectorAndLexicalSignaturesCleanly() {
    RetrievalQuery query =
        new RetrievalQuery(
            "E_PAY_42 processPayment", new float[1536], "CODE", 10, Collections.emptyMap());

    List<RetrievedChunk> results = retriever.retrieve(query);

    assertThat(results).isNotEmpty();
    assertThat(results.get(0).content()).contains("E_PAY_42");
  }
}
