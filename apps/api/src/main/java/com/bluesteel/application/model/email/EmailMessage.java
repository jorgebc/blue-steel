package com.bluesteel.application.model.email;

/**
 * Payload for a transactional email sent via {@code EmailPort}. Carries both a plain-text fallback
 * and an HTML body so providers can send a {@code multipart/alternative} message.
 */
public record EmailMessage(String to, String subject, String textBody, String htmlBody) {}
