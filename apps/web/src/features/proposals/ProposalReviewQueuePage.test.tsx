import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { axe } from 'vitest-axe'
import { ProposalReviewQueuePage } from './ProposalReviewQueuePage'
import { useCosignedProposals, useDecideProposal } from '@/api/proposals'
import { useCampaignStore } from '@/store/campaignStore'
import type { Proposal } from '@/types/proposal'
import type { CampaignRole } from '@/types/campaign'

vi.mock('@/api/proposals')

const proposal: Proposal = {
  proposalId: 'p1',
  campaignId: 'c1',
  targetType: 'ACTOR',
  targetId: 'e1',
  ownerId: 'u1',
  status: 'COSIGNED',
  proposedDelta: { description: 'A reformed thief.' },
  sessionId: 's1',
  resultingEntityVersionId: null,
  expiresAt: '2026-07-14T10:00:00Z',
  createdAt: '2026-06-14T10:00:00Z',
}

function setup(role: CampaignRole | null) {
  useCampaignStore.getState().setCampaign('c1', role)
  return render(
    <MemoryRouter>
      <ProposalReviewQueuePage />
    </MemoryRouter>
  )
}

function mockQueue(over: Partial<ReturnType<typeof useCosignedProposals>> = {}) {
  vi.mocked(useCosignedProposals).mockReturnValue({
    data: { proposals: [proposal], page: 0, size: 20, totalCount: 1 },
    isLoading: false,
    isError: false,
    ...over,
  } as ReturnType<typeof useCosignedProposals>)
}

beforeEach(() => {
  vi.clearAllMocks()
  mockQueue()
  vi.mocked(useDecideProposal).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as unknown as ReturnType<typeof useDecideProposal>)
})

describe('ProposalReviewQueuePage', () => {
  it('lists co-signed proposals for a GM', () => {
    setup('gm')
    expect(screen.getByRole('heading', { name: /proposal review queue/i })).toBeInTheDocument()
    expect(screen.getByText('A reformed thief.')).toBeInTheDocument()
  })

  it('shows the empty state when nothing awaits review', () => {
    mockQueue({ data: { proposals: [], page: 0, size: 20, totalCount: 0 } })
    setup('gm')
    expect(screen.getByText(/no proposals are awaiting review/i)).toBeInTheDocument()
  })

  it('redirects a non-GM member away from the queue', () => {
    setup('player')
    expect(
      screen.queryByRole('heading', { name: /proposal review queue/i })
    ).not.toBeInTheDocument()
  })

  it('shows a skeleton while loading', () => {
    mockQueue({ data: undefined, isLoading: true })
    setup('gm')
    expect(screen.getByRole('status', { name: /loading proposals/i })).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = setup('gm')
    expect(await axe(container)).toHaveNoViolations()
  })
})
