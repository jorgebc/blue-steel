import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { axe } from 'vitest-axe'
import { AppShell } from './AppShell'

vi.mock('./Sidebar', () => ({
  Sidebar: () => <nav aria-label="Campaign navigation">sidebar</nav>,
}))

function renderShell() {
  return render(
    <MemoryRouter initialEntries={['/campaigns/c1']}>
      <Routes>
        <Route path="/campaigns/:campaignId" element={<AppShell />}>
          <Route index element={<div>Campaign page</div>} />
        </Route>
      </Routes>
    </MemoryRouter>
  )
}

describe('AppShell', () => {
  it('renders the sidebar and the routed campaign page', () => {
    renderShell()
    expect(screen.getByRole('navigation', { name: /campaign navigation/i })).toBeInTheDocument()
    expect(screen.getByText('Campaign page')).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = renderShell()
    expect(await axe(container)).toHaveNoViolations()
  })
})
