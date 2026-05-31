package com.bluesteel.adapters.out.ai;

import com.bluesteel.application.model.ingestion.EntityContext;
import com.bluesteel.application.model.ingestion.ExtractedMention;
import com.bluesteel.application.model.ingestion.ResolutionOutcome;
import com.bluesteel.application.model.ingestion.ResolvedEntity;
import com.bluesteel.application.port.out.ingestion.EntityResolutionPort;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Returns deterministic, name-based resolution outcomes (zero API cost). "Stranger" → UNCERTAIN,
 * everything else → NEW. Never returns a MATCH because the local mock pipeline has no seeded world
 * state to match against — a phantom matched id would FK-fail at commit (D-063).
 */
@Component
@Profile("!llm-real & !llm-ollama")
public class MockEntityResolutionAdapter implements EntityResolutionPort {

  @Override
  public List<ResolvedEntity> resolve(
      List<ExtractedMention> mentions, List<EntityContext> candidateContext) {
    return mentions.stream().map(this::resolveOne).toList();
  }

  private ResolvedEntity resolveOne(ExtractedMention mention) {
    return switch (mention.name()) {
      case "Stranger" -> new ResolvedEntity(mention, ResolutionOutcome.UNCERTAIN, null);
      default -> new ResolvedEntity(mention, ResolutionOutcome.NEW, null);
    };
  }
}
