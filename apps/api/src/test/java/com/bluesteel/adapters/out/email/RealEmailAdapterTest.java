package com.bluesteel.adapters.out.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluesteel.application.model.email.EmailMessage;
import com.bluesteel.domain.exception.EmailDeliveryException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
@DisplayName("RealEmailAdapter")
class RealEmailAdapterTest {

  private static final String FROM = "admin@bluesteel.test";

  @Mock private JavaMailSender mailSender;

  private RealEmailAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new RealEmailAdapter(mailSender, FROM);
  }

  @Test
  @DisplayName("should send a MIME message with the admin sender, recipient and subject")
  void send_dispatchesMimeMessage() throws Exception {
    when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((jakarta.mail.Session) null));

    adapter.send(
        new EmailMessage("player@example.com", "Welcome", "plain text", "<p>html body</p>"));

    ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(captor.capture());
    MimeMessage sent = captor.getValue();
    assertThat(sent.getSubject()).isEqualTo("Welcome");
    assertThat(sent.getAllRecipients()).hasSize(1);
    assertThat(sent.getAllRecipients()[0]).hasToString("player@example.com");
    assertThat(sent.getFrom()[0]).hasToString(FROM);
  }

  @Test
  @DisplayName("should throw EmailDeliveryException when the SMTP send fails")
  void send_smtpFailure_throwsEmailDeliveryException() {
    when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((jakarta.mail.Session) null));
    doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

    assertThatThrownBy(
            () ->
                adapter.send(
                    new EmailMessage("player@example.com", "Welcome", "plain", "<p>x</p>")))
        .isInstanceOf(EmailDeliveryException.class);
  }
}
