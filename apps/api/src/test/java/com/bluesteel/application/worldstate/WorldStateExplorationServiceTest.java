package com.bluesteel.application.worldstate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.bluesteel.application.model.worldstate.EntityDetailView;
import com.bluesteel.application.model.worldstate.EntityListFilter;
import com.bluesteel.application.model.worldstate.EntityListPage;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.worldstate.WorldStateReadPort;
import com.bluesteel.application.service.worldstate.WorldStateExplorationService;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.EntityNotFoundException;
import com.bluesteel.domain.exception.UnauthorizedException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorldStateExplorationService")
class WorldStateExplorationServiceTest {

  @Mock private CampaignMembershipPort membershipPort;
  @Mock private WorldStateReadPort readPort;

  private WorldStateExplorationService sut;

  private static final UUID CALLER_ID = UUID.randomUUID();
  private static final UUID CAMPAIGN_ID = UUID.randomUUID();
  private static final UUID ENTITY_ID = UUID.randomUUID();

  @org.junit.jupiter.api.BeforeEach
  void setUp() {
    sut = new WorldStateExplorationService(membershipPort, readPort);
  }

  @Test
  @DisplayName("should throw UnauthorizedException when listing as a non-member")
  void list_nonMember_throwsUnauthorized() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> sut.list("actor", CAMPAIGN_ID, CALLER_ID, 0, 20))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  @DisplayName("should delegate to the read port and return its page for a member")
  void list_member_delegatesToReadPort() {
    EntityListPage page = new EntityListPage(List.of(), 0, 20, 0L);
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    when(readPort.list("actor", CAMPAIGN_ID, EntityListFilter.none(), 0, 20)).thenReturn(page);

    EntityListPage result = sut.list("actor", CAMPAIGN_ID, CALLER_ID, 0, 20);

    assertThat(result).isSameAs(page);
  }

  @Test
  @DisplayName("should clamp a negative page and oversized page size before delegating")
  void list_clampsPageAndSize() {
    EntityListPage page = new EntityListPage(List.of(), 0, 100, 0L);
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.GM));
    when(readPort.list("actor", CAMPAIGN_ID, EntityListFilter.none(), 0, 100)).thenReturn(page);

    EntityListPage result = sut.list("actor", CAMPAIGN_ID, CALLER_ID, -5, 5000);

    assertThat(result).isSameAs(page);
  }

  @Test
  @DisplayName("should throw UnauthorizedException when reading detail as a non-member")
  void getDetail_nonMember_throwsUnauthorized() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> sut.getDetail("actor", CAMPAIGN_ID, ENTITY_ID, CALLER_ID))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  @DisplayName("should throw EntityNotFoundException when the entity is missing")
  void getDetail_missingEntity_throwsNotFound() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.EDITOR));
    when(readPort.getWithHistory("actor", CAMPAIGN_ID, ENTITY_ID)).thenReturn(null);

    assertThatThrownBy(() -> sut.getDetail("actor", CAMPAIGN_ID, ENTITY_ID, CALLER_ID))
        .isInstanceOf(EntityNotFoundException.class);
  }

  @Test
  @DisplayName("should return the read port's detail for a member when the entity exists")
  void getDetail_member_returnsDetail() {
    EntityDetailView detail =
        new EntityDetailView(ENTITY_ID, "actor", "Aldric", CALLER_ID, Instant.now(), List.of());
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    when(readPort.getWithHistory("actor", CAMPAIGN_ID, ENTITY_ID)).thenReturn(detail);

    EntityDetailView result = sut.getDetail("actor", CAMPAIGN_ID, ENTITY_ID, CALLER_ID);

    assertThat(result).isSameAs(detail);
  }
}
