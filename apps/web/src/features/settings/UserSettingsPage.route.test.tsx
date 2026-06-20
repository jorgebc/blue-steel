import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { UserSettingsPage } from './UserSettingsPage'
import { RequireAuth } from '@/components/domain/RequireAuth'
import { useUpdateProfile } from '@/api/users'
import { useAuthStore } from '@/store/authStore'
import type { UserMeResponse } from '@/types/auth'

vi.mock('@/api/users')

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

/** Mirrors the real route wiring: /settings is a global sibling of /, not campaign-scoped. */
function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/login" element={<div>Login Page</div>} />
        <Route
          path="/settings"
          element={
            <RequireAuth>
              <UserSettingsPage />
            </RequireAuth>
          }
        />
      </Routes>
    </MemoryRouter>
  )
}

describe('UserSettingsPage route', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(useUpdateProfile).mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
    } as unknown as ReturnType<typeof useUpdateProfile>)
  })

  it('renders the settings page when authenticated, with no active campaign', () => {
    useAuthStore.setState({ accessToken: 'tok', currentUser: USER, isInitializing: false })
    renderAt('/settings')
    expect(screen.getByRole('heading', { name: /settings/i })).toBeInTheDocument()
  })

  it('redirects to /login when unauthenticated', () => {
    useAuthStore.setState({ accessToken: null, currentUser: null, isInitializing: false })
    renderAt('/settings')
    expect(screen.getByText('Login Page')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: /settings/i })).not.toBeInTheDocument()
  })
})
