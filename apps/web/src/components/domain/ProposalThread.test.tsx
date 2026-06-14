import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'vitest-axe'
import { ProposalThread } from './ProposalThread'
import { useProposalsForTarget, useCoSignProposal } from '@/api/proposals'
import { useAuthStore } from '@/store/authStore'
import { useCampaignStore } from '@/store/campaignStore'
import type { Proposal } from '@/types/proposal'
import type { CampaignRole } from '@/types/campaign'

vi.mock('@/api/proposals')

const mockUseProposals = vi.mocked(useProposalsForTarget)
const mockUseCoSign = vi.mocked(useCoSignProposal)

const authorId = 'author-0000-0000-0000-000000000000'
const otherId = 'other-0000-0000-0000-000000000000'

const proposal: Proposal = {
  proposalId: 'p1',
  campaignId: 'c1',
  targetType: 'ACTOR',
  targetId: 'e1',
  ownerId: authorId,
  status: 'OPEN',
  proposedDelta: { description: 'A reformed thief.' },
  sessionId: 's1',
  resultingEntityVersionId: null,
  expiresAt: '2026-07-14T10:00:00Z',
  createdAt: '2026-06-14T10:00:00Z',
}

const coSignMutate = vi.fn()

function setStores(currentUserId: string | null, role: CampaignRole | null = 'player') {
  useCampaignStore.getState().setCampaign('c1', role)
  useAuthStore
    .getState()
    .setCurrentUser(
      currentUserId
        ? { id: currentUserId, email: 'x@y.z', isAdmin: false, forcePasswordChange: false }
        : null
    )
}

function mockList(over: Partial<ReturnType<typeof useProposalsForTarget>> = {}) {
  // The backend scopes the list to this target server-side, so the hook returns only its proposals.
  mockUseProposals.mockReturnValue({
    data: { proposals: [proposal], page: 0, size: 1, totalCount: 1 },
    isLoading: false,
    isError: false,
    ...over,
  } as ReturnType<typeof useProposalsForTarget>)
}

beforeEach(() => {
  vi.clearAllMocks()
  setStores(otherId)
  mockList()
  mockUseCoSign.mockReturnValue({
    mutate: coSignMutate,
    isPending: false,
  } as unknown as ReturnType<typeof useCoSignProposal>)
})

describe('ProposalThread', () => {
  it('requests the proposals scoped to this target', () => {
    render(<ProposalThread targetType="ACTOR" targetId="e1" />)
    expect(mockUseProposals).toHaveBeenCalledWith('c1', 'ACTOR', 'e1')
    expect(screen.getByText('A reformed thief.')).toBeInTheDocument()
  })

  it('shows the empty state when this target has no proposals', () => {
    mockList({ data: { proposals: [], page: 0, size: 0, totalCount: 0 } })
    render(<ProposalThread targetType="ACTOR" targetId="e1" />)
    expect(screen.getByText(/no proposals yet/i)).toBeInTheDocument()
  })

  it('offers co-sign to a non-author member on an open proposal', () => {
    setStores(otherId)
    render(<ProposalThread targetType="ACTOR" targetId="e1" />)
    expect(screen.getByRole('button', { name: /co-sign/i })).toBeInTheDocument()
  })

  it('hides co-sign from the proposal author', () => {
    setStores(authorId)
    render(<ProposalThread targetType="ACTOR" targetId="e1" />)
    expect(screen.queryByRole('button', { name: /co-sign/i })).not.toBeInTheDocument()
  })

  it('hides co-sign once a proposal is no longer open', () => {
    mockList({
      data: {
        proposals: [{ ...proposal, status: 'COSIGNED' }],
        page: 0,
        size: 20,
        totalCount: 1,
      },
    })
    setStores(otherId)
    render(<ProposalThread targetType="ACTOR" targetId="e1" />)
    expect(screen.queryByRole('button', { name: /co-sign/i })).not.toBeInTheDocument()
  })

  it('co-signs and shows success feedback', async () => {
    coSignMutate.mockImplementation((_id, opts) => opts?.onSuccess?.())
    render(<ProposalThread targetType="ACTOR" targetId="e1" />)

    await userEvent.click(screen.getByRole('button', { name: /co-sign/i }))

    expect(coSignMutate).toHaveBeenCalledWith(
      'p1',
      expect.objectContaining({ onSuccess: expect.any(Function) })
    )
    expect(screen.getByText(/proposal co-signed/i)).toBeInTheDocument()
  })

  it('shows an error banner when the list fails to load', () => {
    mockList({ data: undefined, isError: true })
    render(<ProposalThread targetType="ACTOR" targetId="e1" />)
    expect(screen.getByText(/could not load proposals/i)).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = render(<ProposalThread targetType="ACTOR" targetId="e1" />)
    expect(await axe(container)).toHaveNoViolations()
  })
})
