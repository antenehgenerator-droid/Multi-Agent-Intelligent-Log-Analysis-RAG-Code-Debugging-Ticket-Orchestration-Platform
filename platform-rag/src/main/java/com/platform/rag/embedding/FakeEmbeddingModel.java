package com.platform.rag.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Test double with per-segment invocation counting for cost verification. */
public class FakeEmbeddingModel implements EmbeddingModel {

  public static final int DIMENSION = 1536;

  private final AtomicInteger embedAllInvocationCount = new AtomicInteger();

  @Override
  public Response<Embedding> embed(String text) {
    embedAllInvocationCount.incrementAndGet();
    return Response.from(Embedding.from(vectorFor(text)));
  }

  @Override
  public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
    embedAllInvocationCount.incrementAndGet();
    List<Embedding> embeddings = new ArrayList<>(segments.size());
    for (TextSegment segment : segments) {
      embeddings.add(Embedding.from(vectorFor(segment.text())));
    }
    return Response.from(embeddings);
  }

  public int getEmbedAllInvocationCount() {
    return embedAllInvocationCount.get();
  }

  public void reset() {
    embedAllInvocationCount.set(0);
  }

  static float[] vectorFor(String text) {
    float[] vector = new float[DIMENSION];
    long seed = text.hashCode();
    for (int i = 0; i < DIMENSION; i++) {
      seed = seed * 31 + i;
      vector[i] = (seed % 1000) / 1000.0f;
    }
    return vector;
  }
}
