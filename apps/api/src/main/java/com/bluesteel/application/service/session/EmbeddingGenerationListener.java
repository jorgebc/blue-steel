package com.bluesteel.application.service.session;

import com.bluesteel.application.event.SessionCommittedEvent;
import com.bluesteel.application.model.embedding.EntityEmbeddingRow;
import com.bluesteel.application.model.worldstate.CommittedEntityVersion;
import com.bluesteel.application.port.out.embedding.EmbeddingPort;
import com.bluesteel.application.port.out.embedding.EntityEmbeddingWritePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Generates and persists entity embeddings after the commit transaction completes (D-063). Runs
 * asynchronously so the commit response is returned immediately. Per-entity failures are logged and
 * swallowed so one bad embedding never aborts the others.
 */
@Component
public class EmbeddingGenerationListener {

  private static final Logger log = LoggerFactory.getLogger(EmbeddingGenerationListener.class);

  private final EmbeddingPort embeddingPort;
  private final EntityEmbeddingWritePort entityEmbeddingWritePort;

  public EmbeddingGenerationListener(
      EmbeddingPort embeddingPort, EntityEmbeddingWritePort entityEmbeddingWritePort) {
    this.embeddingPort = embeddingPort;
    this.entityEmbeddingWritePort = entityEmbeddingWritePort;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onSessionCommitted(SessionCommittedEvent event) {
    for (CommittedEntityVersion version : event.committedVersions()) {
      try {
        float[] embedding = embeddingPort.embed(version.contentToEmbed());
        entityEmbeddingWritePort.insert(
            new EntityEmbeddingRow(
                version.entityType(),
                version.entityId(),
                version.entityVersionId(),
                event.sessionId(),
                embedding,
                version.contentHash()));
      } catch (Exception e) {
        log.error(
            "Embedding generation failed — entityType={} entityId={} entityVersionId={} sessionId={}",
            version.entityType(),
            version.entityId(),
            version.entityVersionId(),
            event.sessionId(),
            e);
      }
    }
  }
}
