package com.bluesteel.domain.exception;

/** Thrown when the email provider rejects or fails to accept a transactional email. */
public class EmailDeliveryException extends RuntimeException {

  public EmailDeliveryException(String message, Throwable cause) {
    super(message, cause);
  }
}
