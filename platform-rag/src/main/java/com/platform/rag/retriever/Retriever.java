package com.platform.rag.retriever;

import com.platform.rag.model.RetrievalQuery;
import com.platform.rag.model.RetrievedChunk;
import java.util.List;

public interface Retriever {

  List<RetrievedChunk> retrieve(RetrievalQuery query);
}
