import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'vitest-axe'
import { ProposalReviewCard } from './ProposalReviewCard'
import { useDecideProposal } from '@/api/proposals'
import { useCampaignStore } from '@/store/campaignStore'
import type { Proposal } from '@/types/proposal'

vi.mock('@/api/proposals')

const decideMutate = vi.fn()

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

beforeEach(() => {
  vi.clearAllMocks()
  useCampaignStore.getState().setCampaign('c1', 'gm')
  vi.mocked(useDecideProposal).mockReturnValue({
    mutate: decideMutate,
    isPending: false,
  } as unknown as ReturnType<typeof useDecideProposal>)
})

describe('ProposalReviewCard', () => {
  it('shows the proposed delta', () => {
    render(<ProposalReviewCard proposal={proposal} onDecided={vi.fn()} />)
    expect(screen.getByText('A reformed thief.')).toBeInTheDocument()
  })

  it('approves with the original delta when unedited (no editedDelta sent)', async () => {
    render(<ProposalReviewCard proposal={proposal} onDecided={vi.fn()} />)
    await userEvent.click(screen.getByRole('button', { name: /approve…/i }))
    const dialog = screen.getByRole('dialog', { name: /approve proposal/i })
    await userEvent.click(within(dialog).getByRole('button', { name: /approve & write version/i }))

    expect(decideMutate).toHaveBeenCalledWith(
      { proposalId: 'p1', body: { decision: 'APPROVE', editedDelta: undefined } },
      expect.objectContaining({ onSuccess: expect.any(Function), onError: expect.any(Function) })
    )
  })

  it('sends the full edited delta when the GM edits a field', async () => {
    render(<ProposalReviewCard proposal={proposal} onDecided={vi.fn()} />)
    await userEvent.click(screen.getByRole('button', { name: /approve…/i }))
    const dialog = screen.getByRole('dialog', { name: /approve proposal/i })
    const input = within(dialog).getByLabelText('description')
    await userEvent.clear(input)
    await userEvent.type(input, 'A retired thief.')
    await userEvent.click(within(dialog).getByRole('button', { name: /approve & write version/i }))

    expect(decideMutate).toHaveBeenCalledWith(
      { proposalId: 'p1', body: { decision: 'APPROVE', editedDelta: { description: 'A retired thief.' } } },
      expect.anything()
    )
  })

  it('raises the outcome on a successful approval', async () => {
    decideMutate.mockImplementation((_vars, opts) =>
      opts?.onSuccess?.({ resultingEntityVersionId: 'v9' })
    )
    const onDecided = vi.fn()
    render(<ProposalReviewCard proposal={proposal} onDecided={onDecided} />)
    await userEvent.click(screen.getByRole('button', { name: /approve…/i }))
    const dialog = screen.getByRole('dialog', { name: /approve proposal/i })
    await userEvent.click(within(dialog).getByRole('button', { name: /approve & write version/i }))

    expect(onDecided).toHaveBeenCalledWith({ proposal, resultingEntityVersionId: 'v9' })
  })

  it('vetoes through a confirm overlay', async () => {
    render(<ProposalReviewCard proposal={proposal} onDecided={vi.fn()} />)
    await userEvent.click(screen.getByRole('button', { name: /^veto$/i }))
    const dialog = screen.getByRole('dialog', { name: /veto proposal/i })
    await userEvent.click(within(dialog).getByRole('button', { name: /^veto$/i }))

    expect(decideMutate).toHaveBeenCalledWith(
      { proposalId: 'p1', body: { decision: 'REJECT' } },
      expect.anything()
    )
  })

  it('shows an error banner when the decision fails', async () => {
    decideMutate.mockImplementation((_vars, opts) => opts?.onError?.(new Error('boom')))
    render(<ProposalReviewCard proposal={proposal} onDecided={vi.fn()} />)
    await userEvent.click(screen.getByRole('button', { name: /^veto$/i }))
    const dialog = screen.getByRole('dialog', { name: /veto proposal/i })
    await userEvent.click(within(dialog).getByRole('button', { name: /^veto$/i }))

    expect(screen.getByText(/unexpected error/i)).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = render(<ProposalReviewCard proposal={proposal} onDecided={vi.fn()} />)
    expect(await axe(container)).toHaveNoViolations()
  })
})
