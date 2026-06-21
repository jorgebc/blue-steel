import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { axe } from 'vitest-axe'
import { AppBar } from './AppBar'
import i18n from '@/i18n'
import { useAuthStore } from '@/store/authStore'

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return { ...actual, useNavigate: () => mockNavigate }
})

vi.mock('@/api/users', () => ({
  useUpdateProfile: () => ({ mutate: vi.fn() }),
}))

function setup({ isAdmin = false } = {}) {
  useAuthStore.setState({
    currentUser: {
      id: 'u1',
      email: 'gm@example.com',
      isAdmin,
      forcePasswordChange: false,
      displayName: null,
      avatarAccentColor: null,
      uiLocale: 'en',
      theme: 'system',
    },
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

  it('shows the current user email inside the account menu', async () => {
    setup()
    await userEvent.click(screen.getByRole('button', { name: /account menu/i }))
    expect(screen.getAllByText('gm@example.com').length).toBeGreaterThan(0)
  })

  it('logs out from the account menu and navigates to /login', async () => {
    setup()
    await userEvent.click(screen.getByRole('button', { name: /account menu/i }))
    await userEvent.click(screen.getByRole('menuitem', { name: /log out/i }))
    expect(useAuthStore.getState().currentUser).toBeNull()
    expect(mockNavigate).toHaveBeenCalledWith('/login')
  })

  it('shows the admin badge for admin users', () => {
    setup({ isAdmin: true })
    expect(screen.getByText('Admin')).toBeInTheDocument()
  })

  it('translates the admin badge to Spanish when the locale is es', async () => {
    await i18n.changeLanguage('es')
    setup({ isAdmin: true })
    expect(screen.getByText('Administrador')).toBeInTheDocument()
    expect(screen.queryByText('Admin')).not.toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = setup()
    expect(await axe(container)).toHaveNoViolations()
  })
})
