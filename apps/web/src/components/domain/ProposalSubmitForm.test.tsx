import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'vitest-axe'
import { ProposalSubmitForm } from './ProposalSubmitForm'
import { useCreateProposal } from '@/api/proposals'
import { useSessions } from '@/api/sessions'
import { useCampaignStore } from '@/store/campaignStore'

vi.mock('@/api/proposals')
vi.mock('@/api/sessions')

const mockUseCreate = vi.mocked(useCreateProposal)
const mockUseSessions = vi.mocked(useSessions)

const createMutate = vi.fn()

beforeEach(() => {
  vi.clearAllMocks()
  useCampaignStore.getState().setCampaign('c1', 'player')
  mockUseSessions.mockReturnValue({
    data: [
      { sessionId: 's1', status: 'COMMITTED', sequenceNumber: 1, committedAt: null, createdAt: '' },
    ],
  } as unknown as ReturnType<typeof useSessions>)
  mockUseCreate.mockReturnValue({
    mutate: createMutate,
    isPending: false,
  } as unknown as ReturnType<typeof useCreateProposal>)
})

const snapshot = { name: 'Ari', description: 'A thief.', aliases: ['Shadow'] }

describe('ProposalSubmitForm', () => {
  it('renders an editable input for each primitive snapshot field', () => {
    render(
      <ProposalSubmitForm
        targetType="ACTOR"
        targetId="e1"
        currentSnapshot={snapshot}
        onSubmitted={vi.fn()}
        onCancel={vi.fn()}
      />
    )
    expect(screen.getByLabelText('name')).toHaveValue('Ari')
    expect(screen.getByLabelText('description')).toHaveValue('A thief.')
    // The array field is not editable.
    expect(screen.queryByLabelText('aliases')).not.toBeInTheDocument()
  })

  it('requires a session before submitting', async () => {
    render(
      <ProposalSubmitForm
        targetType="ACTOR"
        targetId="e1"
        currentSnapshot={snapshot}
        onSubmitted={vi.fn()}
        onCancel={vi.fn()}
      />
    )
    await userEvent.click(screen.getByRole('button', { name: /submit proposal/i }))
    expect(screen.getByText(/select the session/i)).toBeInTheDocument()
    expect(createMutate).not.toHaveBeenCalled()
  })

  it('invokes cancel without submitting', async () => {
    const onCancel = vi.fn()
    render(
      <ProposalSubmitForm
        targetType="ACTOR"
        targetId="e1"
        currentSnapshot={snapshot}
        onSubmitted={vi.fn()}
        onCancel={onCancel}
      />
    )
    await userEvent.click(screen.getByRole('button', { name: /cancel/i }))
    expect(onCancel).toHaveBeenCalled()
    expect(createMutate).not.toHaveBeenCalled()
  })

  it('has no accessibility violations', async () => {
    const { container } = render(
      <ProposalSubmitForm
        targetType="ACTOR"
        targetId="e1"
        currentSnapshot={snapshot}
        onSubmitted={vi.fn()}
        onCancel={vi.fn()}
      />
    )
    expect(await axe(container)).toHaveNoViolations()
  })
})
