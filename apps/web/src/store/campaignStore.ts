import { create } from 'zustand'
import type { CampaignRole } from '@/types/campaign'

interface CampaignState {
  activeCampaignId: string | null
  activeRole: CampaignRole | null
  setCampaign: (campaignId: string, role: CampaignRole) => void
  clearCampaign: () => void
}

export const useCampaignStore = create<CampaignState>((set) => ({
  activeCampaignId: null,
  activeRole: null,
  setCampaign: (campaignId, role) => set({ activeCampaignId: campaignId, activeRole: role }),
  clearCampaign: () => set({ activeCampaignId: null, activeRole: null }),
}))
