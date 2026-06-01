package com.bluesteel.adapters.out.email;

import com.bluesteel.application.model.email.EmailMessage;
import com.bluesteel.application.port.out.email.EmailPort;
import com.bluesteel.domain.exception.EmailDeliveryException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Sends transactional emails over SMTP (Gmail) via the auto-configured {@link JavaMailSender}, as a
 * {@code multipart/alternative} message (plain-text + HTML). Active on the {@code email-real}
 * profile (D-075). The sender address is the bootstrap admin email, which must match the
 * authenticated Gmail account. Failures throw {@link EmailDeliveryException}, which rolls back the
 * surrounding invite transaction so an admin can retry without leaving an un-notified account.
 */
@Component
@Profile("email-real")
public class RealEmailAdapter implements EmailPort {

  private static final Logger log = LoggerFactory.getLogger(RealEmailAdapter.class);

  private final JavaMailSender mailSender;
  private final String from;

  public RealEmailAdapter(JavaMailSender mailSender, @Value("${admin.email}") String from) {
    this.mailSender = mailSender;
    this.from = from;
  }

  @Override
  public void send(EmailMessage message) {
    try {
      MimeMessage mime = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
      helper.setFrom(from);
      helper.setTo(message.to());
      helper.setSubject(message.subject());
      helper.setText(message.textBody(), message.htmlBody());
      mailSender.send(mime);
    } catch (MailException | MessagingException e) {
      // Recipient/subject only — the body may carry a temporary password (LOG-02).
      log.error("Failed to send email to={} subject='{}'", message.to(), message.subject(), e);
      throw new EmailDeliveryException("SMTP provider rejected the message", e);
    }
  }
}
