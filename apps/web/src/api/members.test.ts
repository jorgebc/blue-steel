import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { createElement, type ReactNode } from 'react'
import { apiClient } from './client'
import {
  changeMemberRole,
  getCampaignMembers,
  inviteCampaignMember,
  memberKeys,
  removeMember,
  useInviteCampaignMember,
} from './members'
import { createTestQueryClient } from '@/test/createTestQueryClient'
import type { CampaignMemberResponse } from '@/types/member'

vi.mock('./client', () => ({
  apiClient: { get: vi.fn(), post: vi.fn(), patch: vi.fn(), delete: vi.fn() },
}))

const member: CampaignMemberResponse = {
  userId: 'u2',
  email: 'player@example.com',
  role: 'player',
  joinedAt: '2026-01-01T00:00:00Z',
}

function envelope<T>(data: T) {
  return { data, meta: null, errors: [] }
}

describe('members API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('getCampaignMembers() GETs the roster endpoint and unwraps the envelope', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(envelope([member]))

    const result = await getCampaignMembers('c1')

    expect(apiClient.get).toHaveBeenCalledWith('/api/v1/campaigns/c1/members')
    expect(result).toEqual([member])
  })

  it('inviteCampaignMember() POSTs the invitation with an uppercase wire role', async () => {
    vi.mocked(apiClient.post).mockResolvedValue(envelope(null))

    await inviteCampaignMember('c1', { email: 'new@example.com', role: 'editor' })

    expect(apiClient.post).toHaveBeenCalledWith('/api/v1/campaigns/c1/invitations', {
      email: 'new@example.com',
      role: 'EDITOR',
    })
  })

  it('changeMemberRole() PATCHes the member with an uppercase wire role', async () => {
    vi.mocked(apiClient.patch).mockResolvedValue(envelope(null))

    await changeMemberRole('c1', 'u2', 'editor')

    expect(apiClient.patch).toHaveBeenCalledWith('/api/v1/campaigns/c1/members/u2', {
      role: 'EDITOR',
    })
  })

  it('removeMember() DELETEs the member endpoint', async () => {
    vi.mocked(apiClient.delete).mockResolvedValue(envelope(null))

    await removeMember('c1', 'u2')

    expect(apiClient.delete).toHaveBeenCalledWith('/api/v1/campaigns/c1/members/u2')
  })
})

describe('useInviteCampaignMember', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('invalidates the roster cache on success', async () => {
    vi.mocked(apiClient.post).mockResolvedValue(envelope(null))
    const queryClient = createTestQueryClient()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')
    const localWrapper = ({ children }: { children: ReactNode }) =>
      createElement(QueryClientProvider, { client: queryClient }, children)

    const { result } = renderHook(() => useInviteCampaignMember('c1'), { wrapper: localWrapper })
    result.current.mutate({ email: 'new@example.com', role: 'player' })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: memberKeys.all('c1') })
  })
})
