import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'vitest-axe'
import { ConflictWarningCard } from './ConflictWarningCard'
import type { ConflictCard } from '@/types/session'

const conflict: ConflictCard = {
  conflictId: 'k1',
  entityId: 'ent-1',
  entityType: 'actor',
  description: 'The summary contradicts the recorded allegiance.',
  extractedFact: 'Allied with the party',
  existingFact: 'Sworn enemy of the party',
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('ConflictWarningCard', () => {
  it('renders an alert with the description and both facts', () => {
    render(<ConflictWarningCard conflict={conflict} acknowledged={false} onAcknowledge={vi.fn()} />)

    const alert = screen.getByRole('alert')
    expect(alert).toHaveTextContent('The summary contradicts the recorded allegiance.')
    expect(alert).toHaveTextContent('Allied with the party')
    expect(alert).toHaveTextContent('Sworn enemy of the party')
  })

  it('calls onAcknowledge with the conflict id when the checkbox is checked', async () => {
    const onAcknowledge = vi.fn()
    render(
      <ConflictWarningCard conflict={conflict} acknowledged={false} onAcknowledge={onAcknowledge} />
    )

    await userEvent.click(screen.getByRole('checkbox', { name: /acknowledge this conflict/i }))

    expect(onAcknowledge).toHaveBeenCalledWith('k1')
  })

  it('shows the checkbox as checked when already acknowledged', () => {
    render(<ConflictWarningCard conflict={conflict} acknowledged onAcknowledge={vi.fn()} />)

    expect(screen.getByRole('checkbox', { name: /acknowledge this conflict/i })).toBeChecked()
  })

  it('has no accessibility violations', async () => {
    const { container } = render(
      <ConflictWarningCard conflict={conflict} acknowledged={false} onAcknowledge={vi.fn()} />
    )

    expect(await axe(container)).toHaveNoViolations()
  })
})
