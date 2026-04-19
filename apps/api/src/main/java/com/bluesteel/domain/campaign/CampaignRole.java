package com.bluesteel.domain.campaign;

/**
 * Campaign-level membership role. Resolved from the database on every authorized request (D-043).
 */
public enum CampaignRole {
  GM,
  EDITOR,
  PLAYER
}
