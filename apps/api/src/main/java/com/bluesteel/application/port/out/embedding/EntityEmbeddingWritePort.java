package com.bluesteel.application.port.out.embedding;

import com.bluesteel.application.model.embedding.EntityEmbeddingRow;

/** Driven port: inserts one embedding row per committed entity version (D-063). */
public interface EntityEmbeddingWritePort {

  void insert(EntityEmbeddingRow row);
}
