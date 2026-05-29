import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { axe } from 'vitest-axe'
import { AppBar } from './AppBar'
import { useAuthStore } from '@/store/authStore'

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return { ...actual, useNavigate: () => mockNavigate }
})

function setup() {
  useAuthStore.setState({
    currentUser: { id: 'u1', email: 'gm@example.com', isAdmin: false, forcePasswordChange: false },
  })
  return render(
    <MemoryRouter>
      <AppBar />
    </MemoryRouter>
  )
}

describe('AppBar', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('links the brand to the campaign list', () => {
    setup()
    expect(screen.getByRole('link', { name: /blue steel/i })).toHaveAttribute('href', '/')
  })

  it('shows the current user email', () => {
    setup()
    expect(screen.getByText('gm@example.com')).toBeInTheDocument()
  })

  it('logs out and navigates to /login', async () => {
    setup()
    await userEvent.click(screen.getByRole('button', { name: /log out/i }))
    expect(useAuthStore.getState().currentUser).toBeNull()
    expect(mockNavigate).toHaveBeenCalledWith('/login')
  })

  it('has no accessibility violations', async () => {
    const { container } = setup()
    expect(await axe(container)).toHaveNoViolations()
  })
})
