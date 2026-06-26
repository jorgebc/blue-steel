package com.bluesteel.domain.exception;

/**
 * Thrown when a campaign holds more world-state entities than the configured export cap ({@code
 * campaign.export.max-entities}); raised before any rows are materialized so an oversized export
 * cannot exhaust the Render free-tier heap (D-112).
 */
public class ExportTooLargeException extends DomainException {

  public ExportTooLargeException(String message) {
    super(message);
  }
}
