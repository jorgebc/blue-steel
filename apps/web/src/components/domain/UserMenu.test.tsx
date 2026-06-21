import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { axe } from 'vitest-axe'
import { UserMenu } from './UserMenu'
import { useAuthStore } from '@/store/authStore'
import { useSettingsStore } from '@/store/settingsStore'
import type { UiLocale } from '@/types/auth'

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return { ...actual, useNavigate: () => mockNavigate }
})

const mockMutate = vi.fn()
vi.mock('@/api/users', () => ({
  useUpdateProfile: () => ({ mutate: mockMutate }),
}))

function setup({ displayName = 'Jorge Buffa' as string | null, uiLocale = 'en' as UiLocale } = {}) {
  useAuthStore.setState({
    currentUser: {
      id: 'u1',
      email: 'gm@example.com',
      isAdmin: false,
      forcePasswordChange: false,
      displayName,
      avatarAccentColor: null,
      uiLocale,
      theme: 'system',
    },
  })
  useSettingsStore.setState({ theme: 'system', uiLocale })
  return render(
    <MemoryRouter>
      <UserMenu />
    </MemoryRouter>
  )
}

describe('UserMenu', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders the avatar trigger with an account-menu label', () => {
    setup()
    expect(screen.getByRole('button', { name: /account menu/i })).toBeInTheDocument()
  })

  it('opens with the keyboard and reveals the name, email, and a Settings link', async () => {
    const user = userEvent.setup()
    setup()
    screen.getByRole('button', { name: /account menu/i }).focus()
    await user.keyboard('{Enter}')

    expect(screen.getByText('Jorge Buffa')).toBeInTheDocument()
    expect(screen.getByText('gm@example.com')).toBeInTheDocument()
    expect(screen.getByRole('menuitem', { name: /settings/i })).toHaveAttribute('href', '/settings')
  })

  it('closes when Escape is pressed', async () => {
    const user = userEvent.setup()
    setup()
    await user.click(screen.getByRole('button', { name: /account menu/i }))
    expect(screen.getByRole('menu')).toBeInTheDocument()

    await user.keyboard('{Escape}')
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })

  it('persists the theme to the store and the server when a theme is chosen', async () => {
    const user = userEvent.setup()
    setup()
    await user.click(screen.getByRole('button', { name: /account menu/i }))
    await user.click(screen.getByRole('menuitemradio', { name: 'Dark' }))

    expect(useSettingsStore.getState().theme).toBe('dark')
    expect(mockMutate).toHaveBeenCalledWith({ theme: 'dark' })
  })

  it('persists the locale to the store and the server when a language is chosen', async () => {
    const user = userEvent.setup()
    setup()
    await user.click(screen.getByRole('button', { name: /account menu/i }))
    await user.click(screen.getByRole('menuitemradio', { name: 'Español' }))

    expect(useSettingsStore.getState().uiLocale).toBe('es')
    expect(mockMutate).toHaveBeenCalledWith({ uiLocale: 'es' })
  })

  it('falls back to the email as the menu name when no display name is set', async () => {
    const user = userEvent.setup()
    setup({ displayName: null })
    await user.click(screen.getByRole('button', { name: /account menu/i }))
    expect(screen.getAllByText('gm@example.com').length).toBeGreaterThan(0)
  })

  it('logs out and navigates to /login', async () => {
    const user = userEvent.setup()
    setup()
    await user.click(screen.getByRole('button', { name: /account menu/i }))
    await user.click(screen.getByRole('menuitem', { name: /log out/i }))

    expect(useAuthStore.getState().currentUser).toBeNull()
    expect(mockNavigate).toHaveBeenCalledWith('/login')
  })

  it('renders the menu labels in Spanish when the locale is es', async () => {
    const user = userEvent.setup()
    setup({ uiLocale: 'es' })
    await user.click(screen.getByRole('button', { name: /menú de cuenta/i }))

    expect(screen.getByRole('menuitem', { name: /configuración/i })).toBeInTheDocument()
    expect(screen.getByRole('menuitem', { name: /cerrar sesión/i })).toBeInTheDocument()
    expect(screen.getByRole('menuitemradio', { name: 'Oscuro' })).toBeInTheDocument()
    // Language names stay literal regardless of UI locale.
    expect(screen.getByRole('menuitemradio', { name: 'Español' })).toBeInTheDocument()
  })

  it('has no accessibility violations when open', async () => {
    const user = userEvent.setup()
    const { container } = setup()
    await user.click(screen.getByRole('button', { name: /account menu/i }))
    expect(await axe(container)).toHaveNoViolations()
  })
})
