import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { axe } from 'vitest-axe'
import { ChangePasswordPage } from './ChangePasswordPage'
import { useChangePassword } from '@/api/users'
import { ApiClientError } from '@/api/client'
import { useAuthStore } from '@/store/authStore'
import type { CurrentUser } from '@/types/auth'

vi.mock('@/api/users')

const mockNavigate = vi.fn()

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>()
  return { ...actual, useNavigate: () => mockNavigate }
})

const mockUseChangePassword = vi.mocked(useChangePassword)

const mockUser: CurrentUser = {
  id: 'user-1',
  email: 'gm@example.com',
  isAdmin: false,
  forcePasswordChange: true,
}

function renderPage() {
  return render(<ChangePasswordPage />, { wrapper: MemoryRouter })
}

beforeEach(() => {
  vi.clearAllMocks()
  useAuthStore.setState({ accessToken: 'tok', currentUser: mockUser })
})

describe('ChangePasswordPage', () => {
  it('has no accessibility violations', async () => {
    mockUseChangePassword.mockReturnValue({ mutate: vi.fn(), isPending: false } as unknown as ReturnType<typeof useChangePassword>)
    const { container } = renderPage()
    expect(await axe(container)).toHaveNoViolations()
  })

  it('clears forcePasswordChange in authStore and navigates to / on success', async () => {
    const mutate = vi.fn((_vars: unknown, opts?: { onSuccess?: () => void }) => {
      opts?.onSuccess?.()
    })
    mockUseChangePassword.mockReturnValue({ mutate, isPending: false } as unknown as ReturnType<typeof useChangePassword>)

    renderPage()
    await userEvent.type(screen.getByLabelText('Current password'), 'temppass')
    await userEvent.type(screen.getByLabelText('New password'), 'newSecurePass!')
    await userEvent.click(screen.getByRole('button', { name: /change password/i }))

    await waitFor(() => {
      expect(useAuthStore.getState().currentUser?.forcePasswordChange).toBe(false)
      expect(mockNavigate).toHaveBeenCalledWith('/', { replace: true })
    })
  })

  it('maps 400 field errors to the corresponding form fields', async () => {
    const mutate = vi.fn((_vars: unknown, opts?: { onError?: (err: unknown) => void }) => {
      opts?.onError?.(
        new ApiClientError('Validation failed', 400, [
          { code: 'INVALID', message: 'Current password is incorrect', field: 'currentPassword' },
        ]),
      )
    })
    mockUseChangePassword.mockReturnValue({ mutate, isPending: false } as unknown as ReturnType<typeof useChangePassword>)

    renderPage()
    await userEvent.type(screen.getByLabelText('Current password'), 'wrongpass')
    await userEvent.type(screen.getByLabelText('New password'), 'newSecurePass!')
    await userEvent.click(screen.getByRole('button', { name: /change password/i }))

    await waitFor(() => {
      expect(screen.getByText('Current password is incorrect')).toBeInTheDocument()
    })
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('shows InlineBanner for non-field errors', async () => {
    const mutate = vi.fn((_vars: unknown, opts?: { onError?: (err: unknown) => void }) => {
      opts?.onError?.(
        new ApiClientError('Forbidden', 403, [
          { code: 'FORBIDDEN', message: 'Session expired. Please log in again.', field: null },
        ]),
      )
    })
    mockUseChangePassword.mockReturnValue({ mutate, isPending: false } as unknown as ReturnType<typeof useChangePassword>)

    renderPage()
    await userEvent.type(screen.getByLabelText('Current password'), 'temppass')
    await userEvent.type(screen.getByLabelText('New password'), 'newSecurePass!')
    await userEvent.click(screen.getByRole('button', { name: /change password/i }))

    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument()
      expect(screen.getByText('Session expired. Please log in again.')).toBeInTheDocument()
    })
  })

  it('disables the submit button with a spinner while the mutation is pending', () => {
    mockUseChangePassword.mockReturnValue({ mutate: vi.fn(), isPending: true } as unknown as ReturnType<typeof useChangePassword>)

    renderPage()
    const button = screen.getByRole('button', { name: /change password/i })
    expect(button).toBeDisabled()
    expect(button).toHaveAttribute('aria-disabled', 'true')
  })
})
