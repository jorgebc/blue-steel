import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { axe } from 'vitest-axe'
import { UserSettingsPage } from './UserSettingsPage'
import { ACCENT_PALETTE } from './accentPalette'
import { useUpdateProfile } from '@/api/users'
import { useAuthStore } from '@/store/authStore'
import { useSettingsStore } from '@/store/settingsStore'
import type { UserMeResponse } from '@/types/auth'

vi.mock('@/api/users')

const mockUseUpdateProfile = vi.mocked(useUpdateProfile)

const USER: UserMeResponse = {
  id: 'u1',
  email: 'frodo@shire.test',
  isAdmin: false,
  forcePasswordChange: false,
  displayName: null,
  avatarAccentColor: null,
  uiLocale: 'en',
  theme: 'system',
}

function renderPage() {
  return render(
    <MemoryRouter>
      <UserSettingsPage />
    </MemoryRouter>
  )
}

describe('UserSettingsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuthStore.setState({ currentUser: USER })
    useSettingsStore.setState({ theme: 'system', uiLocale: 'en' })
    mockUseUpdateProfile.mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
    } as unknown as ReturnType<typeof useUpdateProfile>)
  })

  it('submits the edited display name and shows a success banner', async () => {
    const mutate = vi.fn((_vars: unknown, opts?: { onSuccess?: () => void }) => {
      opts?.onSuccess?.()
    })
    mockUseUpdateProfile.mockReturnValue({ mutate, isPending: false } as unknown as ReturnType<
      typeof useUpdateProfile
    >)

    renderPage()
    await userEvent.type(screen.getByLabelText(/display name/i), 'Frodo Baggins')
    await userEvent.click(screen.getByRole('button', { name: /save changes/i }))

    await waitFor(() =>
      expect(mutate).toHaveBeenCalledWith(
        {
          displayName: 'Frodo Baggins',
          avatarAccentColor: ACCENT_PALETTE[0].hex,
          uiLocale: 'en',
          theme: 'system',
        },
        expect.anything()
      )
    )
    expect(screen.getByText('Settings saved.')).toBeInTheDocument()
  })

  it('sends an empty string when the display name is cleared (backend clear sentinel, D-113)', async () => {
    const mutate = vi.fn()
    mockUseUpdateProfile.mockReturnValue({ mutate, isPending: false } as unknown as ReturnType<
      typeof useUpdateProfile
    >)

    renderPage()
    await userEvent.click(screen.getByRole('button', { name: /save changes/i }))

    await waitFor(() =>
      expect(mutate).toHaveBeenCalledWith(
        expect.objectContaining({ displayName: '' }),
        expect.anything()
      )
    )
  })

  it('updates the avatar preview live as the display name changes', async () => {
    renderPage()
    // With no display name the avatar falls back to the email.
    expect(screen.getByRole('img', { name: 'frodo@shire.test' })).toBeInTheDocument()

    await userEvent.type(screen.getByLabelText(/display name/i), 'Frodo')
    expect(screen.getByRole('img', { name: 'Frodo' })).toBeInTheDocument()
  })

  it('updates the avatar preview accent live when a swatch is picked', async () => {
    renderPage()
    await userEvent.click(screen.getByRole('radio', { name: ACCENT_PALETTE[3].name }))
    expect(screen.getByRole('img', { name: USER.email })).toHaveStyle({
      backgroundColor: ACCENT_PALETTE[3].hex,
    })
  })

  it('has no accessibility violations', async () => {
    const { container } = renderPage()
    expect(await axe(container)).toHaveNoViolations()
  })
})
