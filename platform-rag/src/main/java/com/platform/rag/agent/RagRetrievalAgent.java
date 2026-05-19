package com.platform.rag.agent;

import com.platform.core.agent.Agent;
import com.platform.core.agent.AgentContext;
import com.platform.core.model.AnomalyReport;
import com.platform.core.model.ParsedLogBatch;
import com.platform.rag.model.RetrievalQuery;
import com.platform.rag.model.RetrievedChunk;
import com.platform.rag.retriever.Retriever;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RagRetrievalAgent implements Agent<AnomalyReport, List<RetrievedChunk>> {

  private final Retriever retriever;
  private final EmbeddingModel embeddingModel;
  private final MeterRegistry metrics;

  public RagRetrievalAgent(
      Retriever retriever, EmbeddingModel embeddingModel, MeterRegistry metrics) {
    this.retriever = retriever;
    this.embeddingModel = embeddingModel;
    this.metrics = metrics;
  }

  @Override
  public String name() {
    return "RagRetrievalAgent";
  }

  @Override
  public List<RetrievedChunk> execute(AnomalyReport report, AgentContext ctx) {
    ParsedLogBatch batch = report.logBatch();
    String queryText =
        batch.normalizedMessage() != null && !batch.normalizedMessage().isBlank()
            ? batch.normalizedMessage()
            : batch.fingerprint();

    float[] queryVector = embeddingModel.embed(queryText).content().vector();

    RetrievalQuery query =
        new RetrievalQuery(
            queryText,
            queryVector,
            "CODE",
            10,
            Map.of("repo", batch.service()));

    try {
      return Timer.builder("rag.retrieval_latency_seconds")
          .tag("corpus", query.corpus())
          .register(metrics)
          .recordCallable(
              () -> {
                List<RetrievedChunk> chunks = retriever.retrieve(query);
                metrics.counter("rag.retrieval_results_total").increment(chunks.size());
                return chunks;
              });
    } catch (Exception e) {
      throw new RuntimeException("Retrieval pipeline failure execution context", e);
    }
  }
}
