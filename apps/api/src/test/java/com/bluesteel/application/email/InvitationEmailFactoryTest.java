package com.bluesteel.application.email;

import static org.assertj.core.api.Assertions.assertThat;

import com.bluesteel.application.model.email.EmailMessage;
import com.bluesteel.application.service.email.InvitationEmailFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InvitationEmailFactory")
class InvitationEmailFactoryTest {

  private static final String LOGIN_URL = "https://app.bluesteel.test";

  private final InvitationEmailFactory factory = new InvitationEmailFactory(LOGIN_URL);

  @Test
  @DisplayName("should build a branded platform invitation with credentials and a login CTA")
  void platformInvitation_buildsBrandedEmail() {
    EmailMessage message = factory.platformInvitation("player@example.com", "Temp0Pass!");

    assertThat(message.to()).isEqualTo("player@example.com");
    assertThat(message.subject()).isEqualTo("Your Blue Steel invitation");

    assertThat(message.htmlBody())
        .contains("Blue&nbsp;Steel")
        .contains("player@example.com")
        .contains("Temp0Pass!")
        .contains("href=\"" + LOGIN_URL + "\"")
        .contains("Log in to Blue Steel");

    assertThat(message.textBody())
        .contains("player@example.com")
        .contains("Temp0Pass!")
        .contains(LOGIN_URL);
  }

  @Test
  @DisplayName("should use campaign wording and subject for a campaign invitation")
  void campaignInvitation_usesCampaignWording() {
    EmailMessage message = factory.campaignInvitation("player@example.com", "Temp0Pass!");

    assertThat(message.subject()).isEqualTo("Your Blue Steel campaign invitation");
    assertThat(message.htmlBody()).containsIgnoringCase("campaign");
    assertThat(message.textBody()).containsIgnoringCase("campaign");
  }

  @Test
  @DisplayName("should HTML-escape dynamic values so they cannot break the markup")
  void invitation_escapesHtmlSpecialCharacters() {
    EmailMessage message = factory.platformInvitation("a&b<x>@example.com", "p<a>&\"s");

    assertThat(message.htmlBody())
        .contains("a&amp;b&lt;x&gt;@example.com")
        .contains("p&lt;a&gt;&amp;&quot;s")
        .doesNotContain("<x>");
  }

  @Test
  @DisplayName("should use the first origin when several are configured for the login link")
  void invitation_usesFirstConfiguredOrigin() {
    InvitationEmailFactory multi =
        new InvitationEmailFactory("https://first.test, https://second.test");

    EmailMessage message = multi.platformInvitation("player@example.com", "Temp0Pass!");

    assertThat(message.htmlBody()).contains("href=\"https://first.test\"");
    assertThat(message.htmlBody()).doesNotContain("second.test");
  }
}
