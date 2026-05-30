package com.bluesteel.application.port.out.session;

import com.bluesteel.domain.session.NarrativeBlock;
import java.util.Optional;
import java.util.UUID;

/** Driven port for persisting and reading {@link NarrativeBlock} entities. */
public interface NarrativeBlockRepository {

  /** Persists a narrative block. */
  void save(NarrativeBlock block);

  /** Returns the narrative block for the given session, if one exists. */
  Optional<NarrativeBlock> findBySessionId(UUID sessionId);
}
