import type { CampaignRole } from '@/types/campaign'

/** Mirror of the backend `CampaignMemberResponse` DTO (ARCHITECTURE.md §7.8). */
export interface CampaignMemberResponse {
  userId: string
  email: string
  role: CampaignRole
  joinedAt: string
}

/**
 * Roles a GM may assign on invite or role change. `gm` is never assignable — a
 * campaign has exactly one GM, set atomically at creation (D-061).
 */
export type AssignableRole = Exclude<CampaignRole, 'gm'>
