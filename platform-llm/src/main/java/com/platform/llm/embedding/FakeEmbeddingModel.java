package com.platform.llm.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Test double: deterministic vectors and explicit invocation counting. */
public class FakeEmbeddingModel implements EmbeddingModel {

  public static final int DIMENSION = 1536;

  private final AtomicInteger modelInvocationCount = new AtomicInteger();

  @Override
  public Response<Embedding> embed(String text) {
    modelInvocationCount.incrementAndGet();
    return Response.from(Embedding.from(vectorFor(text)));
  }

  @Override
  public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
    modelInvocationCount.addAndGet(segments.size());
    List<Embedding> embeddings = new ArrayList<>(segments.size());
    for (TextSegment segment : segments) {
      embeddings.add(Embedding.from(vectorFor(segment.text())));
    }
    return Response.from(embeddings);
  }

  public int getModelInvocationCount() {
    return modelInvocationCount.get();
  }

  public void reset() {
    modelInvocationCount.set(0);
  }

  static float[] vectorFor(String text) {
    float[] vector = new float[DIMENSION];
    long seed = ContentHasher.hashText(text).hashCode();
    for (int i = 0; i < DIMENSION; i++) {
      seed = seed * 31 + i;
      vector[i] = (seed % 1000) / 1000.0f;
    }
    return vector;
  }
}
