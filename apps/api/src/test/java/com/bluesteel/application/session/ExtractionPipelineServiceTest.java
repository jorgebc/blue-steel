package com.bluesteel.application.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluesteel.application.model.ingestion.ExtractedMention;
import com.bluesteel.application.model.ingestion.ExtractionResult;
import com.bluesteel.application.port.out.ingestion.NarrativeExtractionPort;
import com.bluesteel.application.port.out.session.SessionRepository;
import com.bluesteel.application.service.session.ExtractionPipelineService;
import com.bluesteel.domain.session.Session;
import com.bluesteel.domain.session.SessionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExtractionPipelineService")
class ExtractionPipelineServiceTest {

  @Mock private NarrativeExtractionPort narrativeExtractionPort;
  @Mock private SessionRepository sessionRepository;

  private ExtractionPipelineService sut;

  @BeforeEach
  void setUp() {
    sut = new ExtractionPipelineService(narrativeExtractionPort, sessionRepository);
  }

  @Test
  @DisplayName("should transition session to PROCESSING before calling extraction port")
  void run_onEntry_transitionsToProcessing() {
    Session session =
        Session.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now());
    ExtractionResult result =
        new ExtractionResult("Header.", List.of(), List.of(), List.of(), List.of());

    ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
    when(narrativeExtractionPort.extract(textCaptor.capture())).thenReturn(result);

    String rawText = "Some summary text.";
    sut.run(session, rawText);

    assertThat(session.status()).isEqualTo(SessionStatus.PROCESSING);
    assertThat(textCaptor.getValue()).isEqualTo(rawText);
  }

  @Test
  @DisplayName("should mark session FAILED with EXTRACTION_FAILED and rethrow when port throws")
  void run_portThrows_marksSessionFailedAndRethrows() {
    Session session =
        Session.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now());
    RuntimeException portError = new RuntimeException("LLM error");
    String rawText = "Some summary text.";

    ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
    when(narrativeExtractionPort.extract(textCaptor.capture())).thenThrow(portError);

    assertThatThrownBy(() -> sut.run(session, rawText)).isSameAs(portError);

    assertThat(session.status()).isEqualTo(SessionStatus.FAILED);
    assertThat(session.failureReason()).isEqualTo("EXTRACTION_FAILED");
    assertThat(textCaptor.getValue()).isEqualTo(rawText);
    // Session was saved twice: once after startProcessing, once after markFailed
    verify(sessionRepository, org.mockito.Mockito.times(2)).save(session);
  }

  @Test
  @DisplayName("should return the ExtractionResult from the port on success")
  void run_portReturnsResult_returnsIt() {
    Session session =
        Session.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now());
    ExtractionResult expected =
        new ExtractionResult(
            "The heroes entered the dungeon.",
            List.of(new ExtractedMention("Kira", "A rogue", "the rogue")),
            List.of(),
            List.of(),
            List.of());
    String rawText = "The heroes entered the dungeon.";

    ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
    when(narrativeExtractionPort.extract(textCaptor.capture())).thenReturn(expected);

    ExtractionResult result = sut.run(session, rawText);

    assertThat(result).isEqualTo(expected);
    assertThat(textCaptor.getValue()).isEqualTo(rawText);
  }
}
