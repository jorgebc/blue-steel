package com.bluesteel.application.session;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluesteel.application.event.SessionCommittedEvent;
import com.bluesteel.application.model.embedding.EntityEmbeddingRow;
import com.bluesteel.application.model.worldstate.CommittedEntityVersion;
import com.bluesteel.application.port.out.embedding.EmbeddingPort;
import com.bluesteel.application.port.out.embedding.EntityEmbeddingWritePort;
import com.bluesteel.application.service.session.EmbeddingGenerationListener;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("EmbeddingGenerationListener")
class EmbeddingGenerationListenerTest {

  private EmbeddingPort embeddingPort;
  private EntityEmbeddingWritePort entityEmbeddingWritePort;
  private EmbeddingGenerationListener listener;

  private final UUID sessionId = UUID.randomUUID();
  private final UUID campaignId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    embeddingPort = mock(EmbeddingPort.class);
    entityEmbeddingWritePort = mock(EntityEmbeddingWritePort.class);
    listener = new EmbeddingGenerationListener(embeddingPort, entityEmbeddingWritePort);
  }

  @Test
  @DisplayName("should embed and insert once per committed entity version")
  void onSessionCommitted_twoVersions_embedsAndInsertsForEach() {
    CommittedEntityVersion v1 =
        new CommittedEntityVersion(
            "actor", UUID.randomUUID(), UUID.randomUUID(), 1, "Frodo\n{}", "hash1");
    CommittedEntityVersion v2 =
        new CommittedEntityVersion(
            "space", UUID.randomUUID(), UUID.randomUUID(), 1, "Shire\n{}", "hash2");

    float[] embedding1 = new float[] {0.1f, 0.2f};
    float[] embedding2 = new float[] {0.3f, 0.4f};
    when(embeddingPort.embed(v1.contentToEmbed())).thenReturn(embedding1);
    when(embeddingPort.embed(v2.contentToEmbed())).thenReturn(embedding2);

    SessionCommittedEvent event = new SessionCommittedEvent(sessionId, campaignId, List.of(v1, v2));
    listener.onSessionCommitted(event);

    verify(embeddingPort, times(2)).embed(org.mockito.ArgumentMatchers.anyString());
    verify(entityEmbeddingWritePort, times(2))
        .insert(org.mockito.ArgumentMatchers.any(EntityEmbeddingRow.class));
  }

  @Test
  @DisplayName("should swallow embed failure for one version and still process the other")
  void onSessionCommitted_oneEmbedFails_othersStillInserted() {
    CommittedEntityVersion v1 =
        new CommittedEntityVersion(
            "actor", UUID.randomUUID(), UUID.randomUUID(), 1, "Frodo\n{}", "hash1");
    CommittedEntityVersion v2 =
        new CommittedEntityVersion(
            "space", UUID.randomUUID(), UUID.randomUUID(), 1, "Shire\n{}", "hash2");

    doThrow(new RuntimeException("embed service down"))
        .when(embeddingPort)
        .embed(v1.contentToEmbed());
    when(embeddingPort.embed(v2.contentToEmbed())).thenReturn(new float[] {0.5f});

    SessionCommittedEvent event = new SessionCommittedEvent(sessionId, campaignId, List.of(v1, v2));
    listener.onSessionCommitted(event);

    // v1 failed embed → no insert for v1; v2 succeeds
    ArgumentCaptor<EntityEmbeddingRow> captor = ArgumentCaptor.forClass(EntityEmbeddingRow.class);
    verify(entityEmbeddingWritePort, times(1)).insert(captor.capture());
    org.assertj.core.api.Assertions.assertThat(captor.getValue().entityVersionId())
        .isEqualTo(v2.entityVersionId());
  }
}
