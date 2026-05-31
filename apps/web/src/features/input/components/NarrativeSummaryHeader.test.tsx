import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { axe } from 'vitest-axe'
import { NarrativeSummaryHeader } from './NarrativeSummaryHeader'

describe('NarrativeSummaryHeader', () => {
  it('renders the summary text', () => {
    render(<NarrativeSummaryHeader summary="The party escaped the crypt at dawn." />)

    expect(screen.getByText('The party escaped the crypt at dawn.')).toBeInTheDocument()
  })

  it('renders the summary as plain text (no HTML injection)', () => {
    render(<NarrativeSummaryHeader summary="<b>bold</b>" />)

    expect(screen.getByText('<b>bold</b>')).toBeInTheDocument()
    expect(screen.queryByText('bold')).not.toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = render(<NarrativeSummaryHeader summary="A short recap." />)

    expect(await axe(container)).toHaveNoViolations()
  })
})
