package com.bluesteel.application.port.out.ingestion;

import com.bluesteel.application.model.ingestion.EntityContext;
import com.bluesteel.application.model.ingestion.ExtractedMention;
import com.bluesteel.application.model.ingestion.ResolvedEntity;
import java.util.List;

/**
 * Driven port: resolves each extracted mention against existing world-state entities using pgvector
 * similarity search (Stage 1) followed by an LLM disambiguation call (Stage 2).
 */
public interface EntityResolutionPort {

  List<ResolvedEntity> resolve(
      List<ExtractedMention> mentions, List<EntityContext> candidateContext);
}
