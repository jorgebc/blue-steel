package com.bluesteel.application.worldstate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.bluesteel.application.model.worldstate.RelationDetailView;
import com.bluesteel.application.model.worldstate.RelationSummaryView;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.worldstate.RelationReadPort;
import com.bluesteel.application.service.worldstate.RelationExplorationService;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.EntityNotFoundException;
import com.bluesteel.domain.exception.UnauthorizedException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RelationExplorationService")
class RelationExplorationServiceTest {

  @Mock private CampaignMembershipPort membershipPort;
  @Mock private RelationReadPort readPort;

  private RelationExplorationService sut;

  private static final UUID CALLER_ID = UUID.randomUUID();
  private static final UUID CAMPAIGN_ID = UUID.randomUUID();
  private static final UUID RELATION_ID = UUID.randomUUID();
  private static final UUID ACTOR_ID = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    sut = new RelationExplorationService(membershipPort, readPort);
  }

  @Test
  @DisplayName("should throw UnauthorizedException when listing as a non-member")
  void list_nonMember_throwsUnauthorized() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> sut.list(CAMPAIGN_ID, CALLER_ID, null))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  @DisplayName("should delegate to the read port with the actor filter for a member")
  void list_member_delegatesWithActorFilter() {
    RelationSummaryView summary =
        new RelationSummaryView(
            RELATION_ID,
            "bond",
            null,
            ACTOR_ID,
            "actor",
            null,
            null,
            UUID.randomUUID(),
            Instant.now());
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    when(readPort.list(CAMPAIGN_ID, ACTOR_ID)).thenReturn(List.of(summary));

    List<RelationSummaryView> result = sut.list(CAMPAIGN_ID, CALLER_ID, ACTOR_ID);

    assertThat(result).containsExactly(summary);
  }

  @Test
  @DisplayName("should throw UnauthorizedException when reading detail as a non-member")
  void getDetail_nonMember_throwsUnauthorized() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> sut.getDetail(CAMPAIGN_ID, RELATION_ID, CALLER_ID))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  @DisplayName("should throw EntityNotFoundException when the relation is missing")
  void getDetail_missingRelation_throwsNotFound() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.EDITOR));
    when(readPort.getWithHistory(CAMPAIGN_ID, RELATION_ID)).thenReturn(null);

    assertThatThrownBy(() -> sut.getDetail(CAMPAIGN_ID, RELATION_ID, CALLER_ID))
        .isInstanceOf(EntityNotFoundException.class);
  }

  @Test
  @DisplayName("should return the read port's detail for a member when the relation exists")
  void getDetail_member_returnsDetail() {
    RelationDetailView detail =
        new RelationDetailView(
            RELATION_ID,
            "bond",
            "alliance",
            ACTOR_ID,
            "actor",
            null,
            null,
            CALLER_ID,
            Instant.now(),
            List.of());
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.GM));
    when(readPort.getWithHistory(CAMPAIGN_ID, RELATION_ID)).thenReturn(detail);

    RelationDetailView result = sut.getDetail(CAMPAIGN_ID, RELATION_ID, CALLER_ID);

    assertThat(result).isSameAs(detail);
  }
}
