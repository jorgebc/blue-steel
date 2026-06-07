package com.bluesteel.application.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.bluesteel.application.model.query.Citation;
import com.bluesteel.application.model.query.QueryResponse;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.port.out.query.QueryAnsweringPort;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.QueryTimeoutException;
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
@DisplayName("QueryService")
class QueryServiceTest {

  @Mock private CampaignMembershipPort membershipPort;
  @Mock private QueryAnsweringPort queryAnsweringPort;

  private QueryService sut;

  private static final UUID CALLER_ID = UUID.randomUUID();
  private static final UUID CAMPAIGN_ID = UUID.randomUUID();
  private static final String QUESTION = "Who is Mira?";

  @BeforeEach
  void setUp() {
    sut = new QueryService(membershipPort, queryAnsweringPort, 1);
  }

  @Test
  @DisplayName("should throw UnauthorizedException when caller is not a campaign member")
  void answer_nonMember_throwsUnauthorized() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> sut.answer(CAMPAIGN_ID, CALLER_ID, QUESTION))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  @DisplayName("should answer for a PLAYER member — Query Mode is open to every campaign member")
  void answer_playerMember_returnsAnswer() {
    QueryResponse expected =
        new QueryResponse(
            "Mira is a rogue.", List.of(new Citation(UUID.randomUUID(), 1, "Mira appears.")));
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.PLAYER));
    when(queryAnsweringPort.answer(QUESTION, List.of())).thenReturn(expected);

    QueryResponse actual = sut.answer(CAMPAIGN_ID, CALLER_ID, QUESTION);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @DisplayName("should delegate to the answering port and return its response for a GM member")
  void answer_gmMember_delegatesToPort() {
    QueryResponse expected = new QueryResponse("Answer.", List.of());
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.GM));
    when(queryAnsweringPort.answer(QUESTION, List.of())).thenReturn(expected);

    QueryResponse actual = sut.answer(CAMPAIGN_ID, CALLER_ID, QUESTION);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @DisplayName("should throw QueryTimeoutException when answering exceeds the deadline")
  void answer_slowPort_throwsQueryTimeout() {
    when(membershipPort.resolveRole(CAMPAIGN_ID, CALLER_ID))
        .thenReturn(Optional.of(CampaignRole.EDITOR));
    when(queryAnsweringPort.answer(QUESTION, List.of()))
        .thenAnswer(
            invocation -> {
              Thread.sleep(2_000);
              return new QueryResponse("too late", List.of());
            });

    assertThatThrownBy(() -> sut.answer(CAMPAIGN_ID, CALLER_ID, QUESTION))
        .isInstanceOf(QueryTimeoutException.class);
  }
}
