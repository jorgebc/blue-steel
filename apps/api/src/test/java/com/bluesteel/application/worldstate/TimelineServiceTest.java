package com.bluesteel.application.worldstate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.bluesteel.application.model.worldstate.TimelineFilter;
import com.bluesteel.application.model.worldstate.TimelinePage;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.worldstate.TimelineReadPort;
import com.bluesteel.application.service.worldstate.TimelineService;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.UnauthorizedException;
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
@DisplayName("TimelineService")
class TimelineServiceTest {

  @Mock private CampaignMembershipPort membershipPort;
  @Mock private TimelineReadPort timelineReadPort;

  private TimelineService sut;

  private static final UUID CALLER_ID = UUID.randomUUID();
  private static final UUID CAMPAIGN_ID = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    sut = new TimelineService(membershipPort, timelineReadPort);
  }

  @Test
  @DisplayName("should throw UnauthorizedException when reading the timeline as a non-member")
  void getTimeline_nonMember_throwsUnauthorized() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> sut.getTimeline(CAMPAIGN_ID, CALLER_ID, null, 20, TimelineFilter.none()))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  @DisplayName("should delegate to the read port and return its page for a member")
  void getTimeline_member_delegatesToReadPort() {
    TimelinePage page = new TimelinePage(List.of(), null);
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    when(timelineReadPort.page(CAMPAIGN_ID, null, 20, TimelineFilter.none())).thenReturn(page);

    TimelinePage result = sut.getTimeline(CAMPAIGN_ID, CALLER_ID, null, 20, TimelineFilter.none());

    assertThat(result).isSameAs(page);
  }

  @Test
  @DisplayName("should pass the cursor and filter through to the read port unchanged")
  void getTimeline_passesCursorAndFilterThrough() {
    TimelineFilter filter = new TimelineFilter("seraphine", "tavern", "battle");
    TimelinePage page = new TimelinePage(List.of(), "next");
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.GM));
    when(timelineReadPort.page(CAMPAIGN_ID, "cursor-abc", 20, filter)).thenReturn(page);

    TimelinePage result = sut.getTimeline(CAMPAIGN_ID, CALLER_ID, "cursor-abc", 20, filter);

    assertThat(result).isSameAs(page);
    assertThat(result.nextCursor()).isEqualTo("next");
  }

  @Test
  @DisplayName("should clamp a non-positive limit to the default before delegating")
  void getTimeline_clampsNonPositiveLimitToDefault() {
    TimelinePage page = new TimelinePage(List.of(), null);
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    when(timelineReadPort.page(CAMPAIGN_ID, null, 20, TimelineFilter.none())).thenReturn(page);

    TimelinePage result = sut.getTimeline(CAMPAIGN_ID, CALLER_ID, null, 0, TimelineFilter.none());

    assertThat(result).isSameAs(page);
  }

  @Test
  @DisplayName("should clamp an oversized limit to the maximum before delegating")
  void getTimeline_clampsOversizedLimitToMax() {
    TimelinePage page = new TimelinePage(List.of(), null);
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    when(timelineReadPort.page(CAMPAIGN_ID, null, 50, TimelineFilter.none())).thenReturn(page);

    TimelinePage result =
        sut.getTimeline(CAMPAIGN_ID, CALLER_ID, null, 5000, TimelineFilter.none());

    assertThat(result).isSameAs(page);
  }

  @Test
  @DisplayName("should substitute an absent filter with the no-op filter")
  void getTimeline_nullFilter_usesNone() {
    TimelinePage page = new TimelinePage(List.of(), null);
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    when(timelineReadPort.page(CAMPAIGN_ID, null, 20, TimelineFilter.none())).thenReturn(page);

    TimelinePage result = sut.getTimeline(CAMPAIGN_ID, CALLER_ID, null, 20, null);

    assertThat(result).isSameAs(page);
  }
}
