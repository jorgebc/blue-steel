package com.bluesteel.adapters.out.email;

import com.bluesteel.application.model.email.EmailMessage;
import com.bluesteel.application.port.out.email.EmailPort;
import com.bluesteel.domain.exception.EmailDeliveryException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Sends transactional emails via the Brevo HTTP API (D-075). Uses HTTPS (port 443) so it is not
 * blocked by cloud hosting providers that disallow outbound SMTP. Active on the {@code email-real}
 * profile. The sender address is the bootstrap admin email, which must be verified in the Brevo
 * dashboard under Senders &amp; IP → Senders. Failures throw {@link EmailDeliveryException}, which
 * rolls back the surrounding invite transaction so an admin can retry without leaving an
 * un-notified account.
 */
@Component
@Profile("email-real")
public class BrevoEmailAdapter implements EmailPort {

  private static final Logger log = LoggerFactory.getLogger(BrevoEmailAdapter.class);
  private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

  private final RestClient restClient;
  private final String from;

  @Autowired
  public BrevoEmailAdapter(
      RestClient.Builder restClientBuilder,
      @Value("${blue-steel.email.brevo.api-key}") String apiKey,
      @Value("${admin.email}") String from) {
    this.restClient =
        restClientBuilder
            .defaultHeader("api-key", apiKey)
            .defaultHeader("Content-Type", "application/json")
            .build();
    this.from = from;
  }

  /** Package-private constructor for unit tests — accepts a pre-configured {@link RestClient}. */
  BrevoEmailAdapter(RestClient restClient, String from) {
    this.restClient = restClient;
    this.from = from;
  }

  @Override
  public void send(EmailMessage message) {
    Map<String, Object> body =
        Map.of(
            "sender", Map.of("email", from, "name", "Blue Steel"),
            "to", List.of(Map.of("email", message.to())),
            "subject", message.subject(),
            "textContent", message.textBody(),
            "htmlContent", message.htmlBody());
    try {
      restClient.post().uri(BREVO_API_URL).body(body).retrieve().toBodilessEntity();
    } catch (RestClientException e) {
      // Recipient/subject only — the body may carry a temporary password (LOG-02).
      log.error("Failed to send email to={} subject='{}'", message.to(), message.subject(), e);
      throw new EmailDeliveryException("Brevo API call failed", e);
    }
  }
}
