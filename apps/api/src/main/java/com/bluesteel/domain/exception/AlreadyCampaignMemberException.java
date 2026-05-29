package com.bluesteel.domain.exception;

import java.util.UUID;

/** Thrown when inviting a user who already belongs to the campaign (mapped to 409, D-064). */
public class AlreadyCampaignMemberException extends RuntimeException {

  public AlreadyCampaignMemberException(UUID campaignId, UUID userId) {
    super("User " + userId + " is already a member of campaign " + campaignId);
  }
}
