package com.bluesteel.adapters.out.ai;

import com.bluesteel.application.model.ingestion.EntityContext;
import com.bluesteel.application.model.query.QueryResponse;
import com.bluesteel.application.port.out.query.QueryAnsweringPort;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Stub for the real Spring AI query-answering adapter. Active on {@code llm-real} or {@code
 * llm-ollama} profiles; real ChatClient logic is wired in the Query Mode pipeline (Phase 3).
 */
@Component
@Profile("llm-real | llm-ollama")
public class SpringAiQueryAnsweringAdapter implements QueryAnsweringPort {

  @Override
  public QueryResponse answer(String question, List<EntityContext> relevantContext) {
    throw new UnsupportedOperationException(
        "Real LLM adapter not implemented until the Query Mode pipeline (Phase 3)");
  }
}
