import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { axe } from 'vitest-axe'
import { ExplorationLayout } from './ExplorationLayout'

function renderLayout(initialPath = '/campaigns/c1/explore/entities') {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route path="/campaigns/:campaignId/explore" element={<ExplorationLayout />}>
          <Route path="entities" element={<div>Entities view</div>} />
        </Route>
      </Routes>
    </MemoryRouter>
  )
}

describe('ExplorationLayout', () => {
  it('renders nav links for all four exploration views', () => {
    renderLayout()

    const nav = screen.getByRole('navigation', { name: /exploration views/i })
    expect(nav).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /timeline/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /entities/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /spaces/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /relations/i })).toBeInTheDocument()
  })

  it('renders the active child view through the outlet', () => {
    renderLayout()

    expect(screen.getByText('Entities view')).toBeInTheDocument()
  })

  it('marks the Entities tab as current when on the entities route', () => {
    renderLayout()

    expect(screen.getByRole('link', { name: /entities/i })).toHaveClass('text-blue-600')
  })

  it('has no accessibility violations', async () => {
    const { container } = renderLayout()
    expect(await axe(container)).toHaveNoViolations()
  })
})
