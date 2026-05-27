import { describe, it, expect, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { axe } from 'vitest-axe'
import { RequireAuth } from './RequireAuth'
import { useAuthStore } from '@/store/authStore'
import type { CurrentUser } from '@/types/auth'

const mockUser: CurrentUser = {
  id: 'user-1',
  email: 'gm@example.com',
  isAdmin: false,
  forcePasswordChange: false,
}

describe('RequireAuth', () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: null, currentUser: null })
  })

  it('redirects to /login when there is no access token', () => {
    render(
      <MemoryRouter initialEntries={['/dashboard']}>
        <Routes>
          <Route path="/login" element={<div>Login Page</div>} />
          <Route
            path="*"
            element={
              <RequireAuth>
                <div>Protected Content</div>
              </RequireAuth>
            }
          />
        </Routes>
      </MemoryRouter>,
    )

    expect(screen.getByText('Login Page')).toBeInTheDocument()
    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument()
  })

  it('renders children when authenticated with no forced password change', () => {
    useAuthStore.setState({ accessToken: 'tok', currentUser: mockUser })

    render(
      <MemoryRouter initialEntries={['/dashboard']}>
        <Routes>
          <Route
            path="*"
            element={
              <RequireAuth>
                <div>Protected Content</div>
              </RequireAuth>
            }
          />
        </Routes>
      </MemoryRouter>,
    )

    expect(screen.getByText('Protected Content')).toBeInTheDocument()
  })

  it('redirects to /change-password when forcePasswordChange is true and not already there', () => {
    useAuthStore.setState({
      accessToken: 'tok',
      currentUser: { ...mockUser, forcePasswordChange: true },
    })

    render(
      <MemoryRouter initialEntries={['/dashboard']}>
        <Routes>
          <Route path="/change-password" element={<div>Change Password Page</div>} />
          <Route
            path="*"
            element={
              <RequireAuth>
                <div>Protected Content</div>
              </RequireAuth>
            }
          />
        </Routes>
      </MemoryRouter>,
    )

    expect(screen.getByText('Change Password Page')).toBeInTheDocument()
    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument()
  })

  it('renders children at /change-password even when forcePasswordChange is true', () => {
    useAuthStore.setState({
      accessToken: 'tok',
      currentUser: { ...mockUser, forcePasswordChange: true },
    })

    render(
      <MemoryRouter initialEntries={['/change-password']}>
        <Routes>
          <Route
            path="/change-password"
            element={
              <RequireAuth>
                <div>Change Password Form</div>
              </RequireAuth>
            }
          />
        </Routes>
      </MemoryRouter>,
    )

    expect(screen.getByText('Change Password Form')).toBeInTheDocument()
  })

  it('has no accessibility violations when rendering children', async () => {
    useAuthStore.setState({ accessToken: 'tok', currentUser: mockUser })

    const { container } = render(
      <MemoryRouter initialEntries={['/dashboard']}>
        <Routes>
          <Route
            path="*"
            element={
              <RequireAuth>
                <main>
                  <h1>Protected Content</h1>
                </main>
              </RequireAuth>
            }
          />
        </Routes>
      </MemoryRouter>,
    )

    expect(await axe(container)).toHaveNoViolations()
  })
})
