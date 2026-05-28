package com.bluesteel.domain.exception;

import java.util.UUID;

/** Thrown when a campaign with the requested ID does not exist (mapped to 404). */
public class CampaignNotFoundException extends RuntimeException {

  public CampaignNotFoundException(UUID campaignId) {
    super("Campaign not found: " + campaignId);
  }
}
