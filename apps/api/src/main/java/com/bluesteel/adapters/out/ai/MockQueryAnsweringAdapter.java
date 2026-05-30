package com.bluesteel.adapters.out.ai;

import com.bluesteel.application.model.ingestion.EntityContext;
import com.bluesteel.application.model.query.Citation;
import com.bluesteel.application.model.query.QueryResponse;
import com.bluesteel.application.port.out.query.QueryAnsweringPort;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Returns a deterministic canned {@link QueryResponse} with one grounding citation (zero API cost).
 */
@Component
@Profile("!llm-real & !llm-ollama")
public class MockQueryAnsweringAdapter implements QueryAnsweringPort {

  static final UUID CITATION_SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");

  @Override
  public QueryResponse answer(String question, List<EntityContext> relevantContext) {
    var citation = new Citation(CITATION_SESSION_ID, 1, "Mira was first introduced in session 1.");
    return new QueryResponse("This is a mock answer to: " + question, List.of(citation));
  }
}
