package com.bluesteel.application.model.email;

/** Payload for a transactional email sent via {@code EmailPort}. */
public record EmailMessage(String to, String subject, String body) {}
