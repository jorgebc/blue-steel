package com.bluesteel.application.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.bluesteel.application.model.worldstate.EntityDetailView;
import com.bluesteel.application.model.worldstate.EntityVersionView;
import com.bluesteel.application.model.worldstate.EntityWriteCommand;
import com.bluesteel.application.port.out.worldstate.WorldStateReadPort;
import com.bluesteel.application.service.proposal.ProposalDeltaMapper;
import com.bluesteel.domain.exception.ProposalTargetNotFoundException;
import com.bluesteel.domain.proposal.Proposal;
import com.bluesteel.domain.proposal.ProposalStatus;
import com.bluesteel.domain.proposal.ProposalTargetType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProposalDeltaMapper")
class ProposalDeltaMapperTest {

  @Mock private WorldStateReadPort worldStateReadPort;

  private static final UUID CAMPAIGN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID OWNER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID TARGET_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID PROVENANCE_SESSION =
      UUID.fromString("44444444-4444-4444-4444-444444444444");
  private static final UUID LATEST_SESSION =
      UUID.fromString("55555555-5555-5555-5555-555555555555");
  private static final UUID HEAD_OWNER = UUID.fromString("66666666-6666-6666-6666-666666666666");

  @Test
  @DisplayName(
      "should merge the delta over the current snapshot and stamp the latest committed session")
  void toWriteCommand_mergesSnapshotAndStampsLatestSession() {
    ProposalDeltaMapper mapper = new ProposalDeltaMapper(worldStateReadPort);
    when(worldStateReadPort.getWithHistory("actor", CAMPAIGN_ID, TARGET_ID))
        .thenReturn(head(Map.of("name", "Old Name", "alignment", "neutral")));
    Map<String, Object> delta = Map.of("alignment", "chaotic good");

    EntityWriteCommand cmd =
        mapper.toWriteCommand(proposal(ProposalTargetType.ACTOR), delta, LATEST_SESSION);

    assertThat(cmd.entityType()).isEqualTo("actor");
    assertThat(cmd.existingEntityId()).isEqualTo(TARGET_ID);
    assertThat(cmd.campaignId()).isEqualTo(CAMPAIGN_ID);
    assertThat(cmd.ownerId()).isEqualTo(HEAD_OWNER); // preserved from the head, not the author
    assertThat(cmd.name()).isEqualTo("Old Name"); // unchanged when delta has no name
    assertThat(cmd.sessionId()).isEqualTo(LATEST_SESSION); // not the provenance session
    assertThat(cmd.changedFields()).isEqualTo(delta);
    assertThat(cmd.fullSnapshot())
        .containsEntry("name", "Old Name")
        .containsEntry("alignment", "chaotic good");
  }

  @Test
  @DisplayName("should use the delta's name when the delta renames the entity")
  void toWriteCommand_deltaName_overridesHeadName() {
    ProposalDeltaMapper mapper = new ProposalDeltaMapper(worldStateReadPort);
    when(worldStateReadPort.getWithHistory("space", CAMPAIGN_ID, TARGET_ID))
        .thenReturn(head(Map.of("name", "Old Keep")));
    Map<String, Object> delta = Map.of("name", "New Keep");

    EntityWriteCommand cmd =
        mapper.toWriteCommand(proposal(ProposalTargetType.SPACE), delta, LATEST_SESSION);

    assertThat(cmd.name()).isEqualTo("New Keep");
    assertThat(cmd.fullSnapshot()).containsEntry("name", "New Keep");
  }

  @Test
  @DisplayName("should throw ProposalTargetNotFoundException when the target head no longer exists")
  void toWriteCommand_missingHead_throwsTargetNotFound() {
    ProposalDeltaMapper mapper = new ProposalDeltaMapper(worldStateReadPort);
    when(worldStateReadPort.getWithHistory("actor", CAMPAIGN_ID, TARGET_ID)).thenReturn(null);

    assertThatThrownBy(
            () ->
                mapper.toWriteCommand(
                    proposal(ProposalTargetType.ACTOR), Map.of("name", "x"), LATEST_SESSION))
        .isInstanceOf(ProposalTargetNotFoundException.class);
  }

  private static Proposal proposal(ProposalTargetType targetType) {
    Instant now = Instant.now();
    return Proposal.reconstitute(
        UUID.randomUUID(),
        CAMPAIGN_ID,
        targetType,
        TARGET_ID,
        OWNER_ID,
        PROVENANCE_SESSION,
        "{\"name\":\"x\"}",
        ProposalStatus.COSIGNED,
        now.plusSeconds(3600),
        null,
        now);
  }

  private static EntityDetailView head(Map<String, Object> latestSnapshot) {
    Instant now = Instant.now();
    EntityVersionView v1 =
        new EntityVersionView(
            UUID.randomUUID(), 1, PROVENANCE_SESSION, 1, Map.of(), latestSnapshot, now);
    return new EntityDetailView(
        TARGET_ID, "actor", (String) latestSnapshot.get("name"), HEAD_OWNER, now, List.of(v1));
  }
}
