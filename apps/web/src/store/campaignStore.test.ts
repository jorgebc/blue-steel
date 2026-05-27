import { describe, it, expect, beforeEach } from 'vitest'
import { useCampaignStore } from './campaignStore'

describe('campaignStore', () => {
  beforeEach(() => {
    useCampaignStore.setState({ activeCampaignId: null, activeRole: null })
  })

  it('setCampaign stores campaignId and role', () => {
    useCampaignStore.getState().setCampaign('camp-1', 'gm')
    expect(useCampaignStore.getState().activeCampaignId).toBe('camp-1')
    expect(useCampaignStore.getState().activeRole).toBe('gm')
  })

  it('clearCampaign resets activeCampaignId and activeRole to null', () => {
    useCampaignStore.setState({ activeCampaignId: 'camp-1', activeRole: 'editor' })
    useCampaignStore.getState().clearCampaign()
    expect(useCampaignStore.getState().activeCampaignId).toBeNull()
    expect(useCampaignStore.getState().activeRole).toBeNull()
  })
})
