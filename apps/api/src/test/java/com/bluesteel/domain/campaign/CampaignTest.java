package com.bluesteel.domain.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Campaign domain entity")
class CampaignTest {

  private static final UUID ID = UUID.randomUUID();
  private static final UUID CREATED_BY = UUID.randomUUID();
  private static final Instant NOW = Instant.now();

  @Test
  @DisplayName("should create a campaign with valid fields")
  void create_validFields_succeeds() {
    Campaign campaign = Campaign.create(ID, "Dragon Keep", CREATED_BY, NOW);

    assertThat(campaign.id()).isEqualTo(ID);
    assertThat(campaign.name()).isEqualTo("Dragon Keep");
    assertThat(campaign.createdBy()).isEqualTo(CREATED_BY);
    assertThat(campaign.createdAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("should default content language to en when using the short factory")
  void create_shortFactory_defaultsContentLanguageToEn() {
    Campaign campaign = Campaign.create(ID, "Dragon Keep", CREATED_BY, NOW);

    assertThat(campaign.contentLanguage()).isEqualTo("en");
  }

  @Test
  @DisplayName("should store an explicit content language")
  void create_explicitContentLanguage_isStored() {
    Campaign campaign = Campaign.create(ID, "Dragon Keep", CREATED_BY, NOW, "es");

    assertThat(campaign.contentLanguage()).isEqualTo("es");
  }

  @Test
  @DisplayName("should default a null content language to en")
  void create_nullContentLanguage_defaultsToEn() {
    Campaign campaign = Campaign.create(ID, "Dragon Keep", CREATED_BY, NOW, null);

    assertThat(campaign.contentLanguage()).isEqualTo("en");
  }

  @Test
  @DisplayName("should default a blank content language to en")
  void create_blankContentLanguage_defaultsToEn() {
    Campaign campaign = Campaign.create(ID, "Dragon Keep", CREATED_BY, NOW, "  ");

    assertThat(campaign.contentLanguage()).isEqualTo("en");
  }

  @Test
  @DisplayName("should reject a blank name")
  void create_blankName_throwsIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class, () -> Campaign.create(ID, "  ", CREATED_BY, NOW));
  }

  @Test
  @DisplayName("should reject a null name")
  void create_nullName_throwsIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class, () -> Campaign.create(ID, null, CREATED_BY, NOW));
  }
}
