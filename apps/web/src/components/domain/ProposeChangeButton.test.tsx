import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'vitest-axe'
import { ProposeChangeButton } from './ProposeChangeButton'
import { useCreateProposal } from '@/api/proposals'
import { useSessions } from '@/api/sessions'
import { useCampaignStore } from '@/store/campaignStore'

vi.mock('@/api/proposals')
vi.mock('@/api/sessions')

const props = {
  targetType: 'ACTOR' as const,
  targetId: 'e1',
  entityName: 'Ari',
  currentSnapshot: { name: 'Ari', description: 'A thief.' },
}

beforeEach(() => {
  vi.clearAllMocks()
  useCampaignStore.getState().setCampaign('c1', 'player')
  vi.mocked(useSessions).mockReturnValue({ data: [] } as unknown as ReturnType<typeof useSessions>)
  vi.mocked(useCreateProposal).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as unknown as ReturnType<typeof useCreateProposal>)
})

describe('ProposeChangeButton', () => {
  it('renders an enabled "Propose a change" button', () => {
    render(<ProposeChangeButton {...props} />)
    const button = screen.getByRole('button', { name: /propose a change/i })
    expect(button).toBeEnabled()
  })

  it('opens the submission overlay when clicked', async () => {
    render(<ProposeChangeButton {...props} />)
    await userEvent.click(screen.getByRole('button', { name: /propose a change/i }))
    const dialog = screen.getByRole('dialog', { name: /propose a change to ari/i })
    expect(dialog).toBeInTheDocument()
    expect(screen.getByLabelText('description')).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = render(<ProposeChangeButton {...props} />)
    expect(await axe(container)).toHaveNoViolations()
  })
})
