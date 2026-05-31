import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'vitest-axe'
import { CommitButton } from './CommitButton'

beforeEach(() => {
  vi.clearAllMocks()
})

describe('CommitButton', () => {
  it('is enabled and commits when there are no outstanding items', async () => {
    const onCommit = vi.fn()
    render(
      <CommitButton
        unresolvedUncertainCount={0}
        unacknowledgedConflictCount={0}
        isPending={false}
        onCommit={onCommit}
      />
    )

    const button = screen.getByRole('button', { name: /commit to world state/i })
    expect(button).toBeEnabled()
    await userEvent.click(button)
    expect(onCommit).toHaveBeenCalledOnce()
  })

  it('is disabled while UNCERTAIN cards remain unresolved', () => {
    render(
      <CommitButton
        unresolvedUncertainCount={2}
        unacknowledgedConflictCount={0}
        isPending={false}
        onCommit={vi.fn()}
      />
    )

    const button = screen.getByRole('button', { name: /commit to world state/i })
    expect(button).toBeDisabled()
    expect(button).toHaveAttribute('aria-disabled', 'true')
    expect(screen.getByText('2 items require your decision')).toBeInTheDocument()
  })

  it('is disabled while conflicts remain unacknowledged and uses singular wording for one item', () => {
    render(
      <CommitButton
        unresolvedUncertainCount={0}
        unacknowledgedConflictCount={1}
        isPending={false}
        onCommit={vi.fn()}
      />
    )

    expect(screen.getByRole('button', { name: /commit to world state/i })).toBeDisabled()
    expect(screen.getByText('1 item require your decision')).toBeInTheDocument()
  })

  it('is disabled and shows a spinner while a commit is pending', () => {
    render(
      <CommitButton
        unresolvedUncertainCount={0}
        unacknowledgedConflictCount={0}
        isPending
        onCommit={vi.fn()}
      />
    )

    expect(screen.getByRole('button', { name: /commit to world state/i })).toBeDisabled()
  })

  it('has no accessibility violations when disabled', async () => {
    const { container } = render(
      <CommitButton
        unresolvedUncertainCount={1}
        unacknowledgedConflictCount={0}
        isPending={false}
        onCommit={vi.fn()}
      />
    )

    expect(await axe(container)).toHaveNoViolations()
  })
})
