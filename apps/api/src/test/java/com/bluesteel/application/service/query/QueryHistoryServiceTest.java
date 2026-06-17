package com.bluesteel.application.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluesteel.application.model.query.Citation;
import com.bluesteel.application.model.query.QueryHistoryView;
import com.bluesteel.application.model.query.QueryLogEntry;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.query.QueryLogRepository;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.UnauthorizedException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueryHistoryService")
class QueryHistoryServiceTest {

  private static final UUID CAMPAIGN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID CALLER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Mock private CampaignMembershipPort membershipPort;
  @Mock private QueryLogRepository queryLogRepository;
  @InjectMocks private QueryHistoryService service;

  private static QueryLogEntry entry(String question) {
    return new QueryLogEntry(
        UUID.randomUUID(),
        CAMPAIGN_ID,
        CALLER_ID,
        question,
        "An answer.",
        List.of(new Citation(UUID.randomUUID(), 1, "snippet")),
        Instant.parse("2026-06-17T10:00:00Z"));
  }

  @Test
  @DisplayName("should return the page of entries with total count for a member")
  void getHistory_member_returnsPageWithTotals() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    List<QueryLogEntry> entries = List.of(entry("Who is Mira?"), entry("Where is the keep?"));
    when(queryLogRepository.findByCampaign(CAMPAIGN_ID, 0, 20)).thenReturn(entries);
    when(queryLogRepository.countByCampaign(CAMPAIGN_ID)).thenReturn(42L);

    QueryHistoryView view = service.getHistory(CAMPAIGN_ID, CALLER_ID, 0, 20);

    assertThat(view.entries()).isEqualTo(entries);
    assertThat(view.totalCount()).isEqualTo(42L);
    assertThat(view.page()).isZero();
    assertThat(view.size()).isEqualTo(20);
  }

  @Test
  @DisplayName("should throw UnauthorizedException when the caller is not a campaign member")
  void getHistory_nonMember_throwsUnauthorized() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getHistory(CAMPAIGN_ID, CALLER_ID, 0, 20))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  @DisplayName("should clamp an oversized page size to the maximum of 100")
  void getHistory_oversizedSize_clampedToMax() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.GM));
    when(queryLogRepository.findByCampaign(CAMPAIGN_ID, 0, 100)).thenReturn(List.of());
    when(queryLogRepository.countByCampaign(CAMPAIGN_ID)).thenReturn(0L);

    QueryHistoryView view = service.getHistory(CAMPAIGN_ID, CALLER_ID, 0, 500);

    assertThat(view.size()).isEqualTo(100);
    verify(queryLogRepository).findByCampaign(CAMPAIGN_ID, 0, 100);
  }

  @Test
  @DisplayName("should default a non-positive page size to 20")
  void getHistory_nonPositiveSize_defaultsTo20() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    when(queryLogRepository.findByCampaign(CAMPAIGN_ID, 0, 20)).thenReturn(List.of());
    when(queryLogRepository.countByCampaign(CAMPAIGN_ID)).thenReturn(0L);

    QueryHistoryView view = service.getHistory(CAMPAIGN_ID, CALLER_ID, 0, 0);

    assertThat(view.size()).isEqualTo(20);
    verify(queryLogRepository).findByCampaign(CAMPAIGN_ID, 0, 20);
  }

  @Test
  @DisplayName("should clamp a negative page index to zero")
  void getHistory_negativePage_clampedToZero() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    when(queryLogRepository.findByCampaign(CAMPAIGN_ID, 0, 20)).thenReturn(List.of());
    when(queryLogRepository.countByCampaign(CAMPAIGN_ID)).thenReturn(0L);

    QueryHistoryView view = service.getHistory(CAMPAIGN_ID, CALLER_ID, -5, 20);

    assertThat(view.page()).isZero();
    verify(queryLogRepository).findByCampaign(CAMPAIGN_ID, 0, 20);
  }

  @Test
  @DisplayName("should translate the page index to a row offset of page * size")
  void getHistory_paged_computesOffset() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    when(queryLogRepository.findByCampaign(CAMPAIGN_ID, 20, 10)).thenReturn(List.of());
    when(queryLogRepository.countByCampaign(CAMPAIGN_ID)).thenReturn(0L);

    service.getHistory(CAMPAIGN_ID, CALLER_ID, 2, 10);

    verify(queryLogRepository).findByCampaign(CAMPAIGN_ID, 20, 10);
  }
}
