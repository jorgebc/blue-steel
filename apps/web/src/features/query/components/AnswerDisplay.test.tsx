import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { axe } from 'vitest-axe'
import { AnswerDisplay } from './AnswerDisplay'
import type { QueryResponse } from '@/types/query'

const response: QueryResponse = {
  answer: 'Aldric fled north after the battle.',
  citations: [
    { sessionId: 's1', sequenceNumber: 3, snippet: 'Aldric was last seen heading north.' },
  ],
}

function renderDisplay(over: Partial<QueryResponse> = {}) {
  return render(
    <MemoryRouter>
      <AnswerDisplay response={{ ...response, ...over }} campaignId="c1" />
    </MemoryRouter>
  )
}

describe('AnswerDisplay', () => {
  it('renders the answer text', () => {
    renderDisplay()
    expect(screen.getByText('Aldric fled north after the battle.')).toBeInTheDocument()
  })

  it('renders the citation section when citations are present', () => {
    renderDisplay()
    expect(screen.getByRole('heading', { name: /sources/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /session 3/i })).toBeInTheDocument()
  })

  it('omits the citation section when there are no citations', () => {
    renderDisplay({ citations: [] })
    expect(screen.queryByRole('heading', { name: /sources/i })).not.toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = renderDisplay()
    expect(await axe(container)).toHaveNoViolations()
  })
})
