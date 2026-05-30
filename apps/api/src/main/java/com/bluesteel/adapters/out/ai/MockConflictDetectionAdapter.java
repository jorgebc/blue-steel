package com.bluesteel.adapters.out.ai;

import com.bluesteel.application.model.ingestion.ConflictWarning;
import com.bluesteel.application.model.ingestion.EntityContext;
import com.bluesteel.application.model.ingestion.ExtractionResult;
import com.bluesteel.application.port.out.ingestion.ConflictDetectionPort;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Returns one {@link ConflictWarning} on the first call, then an empty list on all subsequent calls
 * (zero API cost). The stateful counter exercises GM diff-review acknowledgement in tests.
 */
@Component
@Profile("!llm-real & !llm-ollama")
public class MockConflictDetectionAdapter implements ConflictDetectionPort {

  private final AtomicInteger callCount = new AtomicInteger(0);

  @Override
  public List<ConflictWarning> detect(
      ExtractionResult extraction, List<EntityContext> relevantContext) {
    if (callCount.getAndIncrement() == 0) {
      return List.of(
          new ConflictWarning(
              "Mira",
              "Previously described as a healer; now referred to as a warrior — possible continuity conflict."));
    }
    return List.of();
  }
}
