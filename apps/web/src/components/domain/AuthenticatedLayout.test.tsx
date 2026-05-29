import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { axe } from 'vitest-axe'
import { AuthenticatedLayout } from './AuthenticatedLayout'

vi.mock('./AppBar', () => ({
  AppBar: () => <header>appbar</header>,
}))

function renderLayout() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route element={<AuthenticatedLayout />}>
          <Route index element={<main>home content</main>} />
        </Route>
      </Routes>
    </MemoryRouter>
  )
}

describe('AuthenticatedLayout', () => {
  it('renders the app bar and the routed page', () => {
    renderLayout()
    expect(screen.getByText('appbar')).toBeInTheDocument()
    expect(screen.getByText('home content')).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = renderLayout()
    expect(await axe(container)).toHaveNoViolations()
  })
})
