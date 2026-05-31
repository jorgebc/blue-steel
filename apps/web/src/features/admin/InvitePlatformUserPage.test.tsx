import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { axe } from 'vitest-axe'
import { InvitePlatformUserPage } from './InvitePlatformUserPage'
import { useInvitePlatformUser } from '@/api/invitations'
import { useAuthStore } from '@/store/authStore'
import type { CurrentUser } from '@/types/auth'

vi.mock('@/api/invitations', () => ({
  useInvitePlatformUser: vi.fn(),
}))

const inviteMutate = vi.fn()

function setAdmin(isAdmin: boolean) {
  useAuthStore.setState({
    currentUser: {
      id: 'a1',
      email: 'admin@example.com',
      isAdmin,
      forcePasswordChange: false,
    } as CurrentUser,
  })
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/invite']}>
      <Routes>
        <Route path="/invite" element={<InvitePlatformUserPage />} />
        <Route path="/" element={<div>campaign list</div>} />
      </Routes>
    </MemoryRouter>
  )
}

describe('InvitePlatformUserPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(useInvitePlatformUser).mockReturnValue({
      mutate: inviteMutate,
      isPending: false,
    } as unknown as ReturnType<typeof useInvitePlatformUser>)
    setAdmin(true)
  })

  it('redirects a non-admin away from the page', () => {
    setAdmin(false)

    renderPage()

    expect(screen.getByText('campaign list')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /send invitation/i })).not.toBeInTheDocument()
  })

  it('invites a user by email', () => {
    renderPage()

    fireEvent.change(screen.getByLabelText(/email/i), {
      target: { value: 'new-user@example.com' },
    })
    fireEvent.click(screen.getByRole('button', { name: /send invitation/i }))

    expect(inviteMutate).toHaveBeenCalledWith('new-user@example.com', expect.anything())
  })

  it('has no accessibility violations', async () => {
    const { container } = renderPage()

    expect(await axe(container)).toHaveNoViolations()
  })
})
