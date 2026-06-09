import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { axe } from 'vitest-axe'
import { CitationList } from './CitationList'
import type { Citation } from '@/types/query'

const citations: Citation[] = [
  { sessionId: 's1', sequenceNumber: 3, snippet: 'Aldric fled north.' },
  { sessionId: 's2', sequenceNumber: 5, snippet: 'The keep fell at dawn.' },
]

function renderList() {
  return render(
    <MemoryRouter>
      <CitationList citations={citations} campaignId="c1" />
    </MemoryRouter>
  )
}

describe('CitationList', () => {
  it('links each citation to its session detail route', () => {
    renderList()

    expect(screen.getByRole('link', { name: /session 3: aldric fled north\./i })).toHaveAttribute(
      'href',
      '/campaigns/c1/sessions/s1'
    )
    expect(
      screen.getByRole('link', { name: /session 5: the keep fell at dawn\./i })
    ).toHaveAttribute('href', '/campaigns/c1/sessions/s2')
  })

  it('renders the supporting snippet text for each citation', () => {
    renderList()
    expect(screen.getByText('Aldric fled north.')).toBeInTheDocument()
    expect(screen.getByText('The keep fell at dawn.')).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = renderList()
    expect(await axe(container)).toHaveNoViolations()
  })
})
