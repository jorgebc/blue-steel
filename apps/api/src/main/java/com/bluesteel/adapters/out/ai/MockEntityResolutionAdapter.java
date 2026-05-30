package com.bluesteel.adapters.out.ai;

import com.bluesteel.application.model.ingestion.EntityContext;
import com.bluesteel.application.model.ingestion.ExtractedMention;
import com.bluesteel.application.model.ingestion.ResolutionOutcome;
import com.bluesteel.application.model.ingestion.ResolvedEntity;
import com.bluesteel.application.port.out.ingestion.EntityResolutionPort;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Returns deterministic, name-based resolution outcomes (zero API cost). "Mira" → MATCH,
 * "Thornwick" → NEW, "Stranger" → UNCERTAIN, all others → NEW.
 */
@Component
@Profile("!llm-real & !llm-ollama")
public class MockEntityResolutionAdapter implements EntityResolutionPort {

  static final UUID MIRA_ENTITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Override
  public List<ResolvedEntity> resolve(
      List<ExtractedMention> mentions, List<EntityContext> candidateContext) {
    return mentions.stream().map(this::resolveOne).toList();
  }

  private ResolvedEntity resolveOne(ExtractedMention mention) {
    return switch (mention.name()) {
      case "Mira" -> new ResolvedEntity(mention, ResolutionOutcome.MATCH, MIRA_ENTITY_ID);
      case "Stranger" -> new ResolvedEntity(mention, ResolutionOutcome.UNCERTAIN, null);
      default -> new ResolvedEntity(mention, ResolutionOutcome.NEW, null);
    };
  }
}
