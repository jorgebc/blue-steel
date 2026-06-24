package com.bluesteel.adapters.in.web.campaign;

import com.bluesteel.application.model.campaign.CampaignView;
import java.time.Instant;
import java.util.UUID;

/** Response DTO for campaign endpoints. Role is lowercase; null when caller is non-member admin. */
public record CampaignResponse(
    UUID id, String name, UUID createdBy, Instant createdAt, String contentLanguage, String role) {

  static CampaignResponse from(CampaignView view) {
    String role = view.role() != null ? view.role().name().toLowerCase() : null;
    return new CampaignResponse(
        view.id(), view.name(), view.createdBy(), view.createdAt(), view.contentLanguage(), role);
  }
}
