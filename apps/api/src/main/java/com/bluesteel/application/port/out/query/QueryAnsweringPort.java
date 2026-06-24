package com.bluesteel.application.port.out.query;

import com.bluesteel.application.model.ingestion.EntityContext;
import com.bluesteel.application.model.query.QueryResponse;
import java.util.List;

/**
 * Driven port: answers a natural-language question about the campaign world state using retrieved
 * entity context and an LLM call. Implemented in the Query Mode pipeline (Phase 3).
 *
 * <p>{@code contentLanguage} is the campaign's content-language code (D-103); the answer prose is
 * produced in that language while retrieval and citation grounding are unaffected.
 */
public interface QueryAnsweringPort {

  QueryResponse answer(
      String question, List<EntityContext> relevantContext, String contentLanguage);
}
