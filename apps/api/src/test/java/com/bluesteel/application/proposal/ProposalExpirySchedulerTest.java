package com.bluesteel.application.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluesteel.application.port.out.proposal.ProposalExpiryPort;
import com.bluesteel.application.service.proposal.ProposalExpiryScheduler;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProposalExpiryScheduler")
class ProposalExpirySchedulerTest {

  @Mock private ProposalExpiryPort proposalExpiryPort;

  @Test
  @DisplayName("should sweep with a cutoff at the current instant")
  void expireStaleProposals_invokesPortWithNowCutoff() {
    ProposalExpiryScheduler sut = new ProposalExpiryScheduler(proposalExpiryPort);
    when(proposalExpiryPort.expireProposals(any(Instant.class))).thenReturn(0);
    Instant before = Instant.now();

    sut.expireStaleProposals();

    ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
    verify(proposalExpiryPort).expireProposals(cutoffCaptor.capture());
    assertThat(cutoffCaptor.getValue()).isBetween(before, Instant.now());
  }
}
