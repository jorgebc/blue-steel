export type CampaignRole = 'gm' | 'editor' | 'player'

/**
 * Mirror of the backend `CampaignResponse` DTO. `role` is the caller's resolved
 * membership role (lowercase, from `campaign_members` — never the JWT); `null`
 * only when an admin lists a campaign they do not belong to. `contentLanguage`
 * is the campaign's immutable content language fixed at creation (D-103).
 */
export interface CampaignResponse {
  id: string
  name: string
  createdBy: string
  createdAt: string
  contentLanguage: string
  role: CampaignRole | null
}

export type Campaign = CampaignResponse
