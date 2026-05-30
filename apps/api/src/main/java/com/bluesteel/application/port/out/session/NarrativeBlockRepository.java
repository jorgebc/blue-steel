package com.bluesteel.application.port.out.session;

import com.bluesteel.domain.session.NarrativeBlock;

/** Driven port for persisting {@link NarrativeBlock} entities. */
public interface NarrativeBlockRepository {

  /** Persists a narrative block. */
  void save(NarrativeBlock block);
}
