import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { axe } from 'vitest-axe'
import { ProposalStatusBadge } from './ProposalStatusBadge'
import type { ProposalStatus } from '@/types/proposal'

const CASES: Array<[ProposalStatus, RegExp]> = [
  ['OPEN', /^open$/i],
  ['COSIGNED', /co-signed/i],
  ['APPROVED', /approved/i],
  ['REJECTED', /rejected/i],
  ['EXPIRED', /expired/i],
]

describe('ProposalStatusBadge', () => {
  it.each(CASES)('renders a readable label for %s', (status, label) => {
    render(<ProposalStatusBadge status={status} />)
    expect(screen.getByText(label)).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = render(<ProposalStatusBadge status="COSIGNED" />)
    expect(await axe(container)).toHaveNoViolations()
  })
})
