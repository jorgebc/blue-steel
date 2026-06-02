package com.bluesteel.adapters.out.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.bluesteel.application.model.email.EmailMessage;
import com.bluesteel.domain.exception.EmailDeliveryException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
@DisplayName("BrevoEmailAdapter")
class BrevoEmailAdapterTest {

  private static final String FROM = "admin@bluesteel.test";
  private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

  @Mock private RestClient restClient;
  @Mock private RestClient.RequestBodyUriSpec requestBodyUriSpec;
  @Mock private RestClient.RequestBodySpec requestBodySpec;
  @Mock private RestClient.ResponseSpec responseSpec;

  private BrevoEmailAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new BrevoEmailAdapter(restClient, FROM);
    when(restClient.post()).thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.uri(BREVO_API_URL)).thenReturn(requestBodySpec);
    when(requestBodySpec.retrieve()).thenReturn(responseSpec);
  }

  @Test
  @DisplayName("should POST correct sender, recipient, subject, and body content to Brevo API")
  void send_postsCorrectRequest() {
    ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.captor();
    when(requestBodySpec.body(bodyCaptor.capture())).thenReturn(requestBodySpec);
    when(responseSpec.toBodilessEntity()).thenReturn(null);

    adapter.send(new EmailMessage("player@example.com", "Welcome", "plain text", "<p>html</p>"));

    Map<String, Object> body = bodyCaptor.getValue();
    assertThat(((Map<?, ?>) body.get("sender")).get("email")).isEqualTo(FROM);
    assertThat(((Map<?, ?>) body.get("sender")).get("name")).isEqualTo("Blue Steel");
    @SuppressWarnings("unchecked")
    List<Map<?, ?>> to = (List<Map<?, ?>>) body.get("to");
    assertThat(to).hasSize(1);
    assertThat(to.get(0).get("email")).isEqualTo("player@example.com");
    assertThat(body.get("subject")).isEqualTo("Welcome");
    assertThat(body.get("textContent")).isEqualTo("plain text");
    assertThat(body.get("htmlContent")).isEqualTo("<p>html</p>");
  }

  @Test
  @DisplayName("should throw EmailDeliveryException when Brevo returns a 4xx client error")
  void send_clientError_throwsEmailDeliveryException() {
    ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.captor();
    when(requestBodySpec.body(bodyCaptor.capture())).thenReturn(requestBodySpec);
    when(responseSpec.toBodilessEntity())
        .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

    assertThatThrownBy(
            () ->
                adapter.send(
                    new EmailMessage("player@example.com", "Welcome", "plain", "<p>x</p>")))
        .isInstanceOf(EmailDeliveryException.class);
  }

  @Test
  @DisplayName("should throw EmailDeliveryException when Brevo returns a 5xx server error")
  void send_serverError_throwsEmailDeliveryException() {
    ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.captor();
    when(requestBodySpec.body(bodyCaptor.capture())).thenReturn(requestBodySpec);
    when(responseSpec.toBodilessEntity())
        .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

    assertThatThrownBy(
            () ->
                adapter.send(
                    new EmailMessage("player@example.com", "Welcome", "plain", "<p>x</p>")))
        .isInstanceOf(EmailDeliveryException.class);
  }
}
