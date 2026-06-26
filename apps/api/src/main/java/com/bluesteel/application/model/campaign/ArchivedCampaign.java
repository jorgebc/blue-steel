package com.bluesteel.application.model.campaign;

import java.time.Instant;
import java.util.UUID;

/** Campaign metadata as carried in a {@link CampaignArchive} export (D-112). */
public record ArchivedCampaign(
    UUID id, String name, UUID createdBy, Instant createdAt, String contentLanguage) {}
