import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { axe } from 'vitest-axe'
import { LoginPage } from './LoginPage'
import { useLogin } from '@/api/auth'
import { ApiClientError } from '@/api/client'
import i18n from '@/i18n'

vi.mock('@/api/auth')

const mockNavigate = vi.fn()

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>()
  return { ...actual, useNavigate: () => mockNavigate }
})

function renderPage() {
  return render(<LoginPage />, { wrapper: MemoryRouter })
}

const mockUseLogin = vi.mocked(useLogin)

beforeEach(() => {
  vi.clearAllMocks()
})

describe('LoginPage', () => {
  it('has no accessibility violations', async () => {
    mockUseLogin.mockReturnValue({ mutate: vi.fn(), isPending: false } as unknown as ReturnType<
      typeof useLogin
    >)
    const { container } = renderPage()
    expect(await axe(container)).toHaveNoViolations()
  })

  it('navigates to / on successful login without forcePasswordChange', async () => {
    const mutate = vi.fn(
      (
        _vars: unknown,
        opts?: { onSuccess?: (data: { accessToken: string; forcePasswordChange: boolean }) => void }
      ) => {
        opts?.onSuccess?.({ accessToken: 'tok', forcePasswordChange: false })
      }
    )
    mockUseLogin.mockReturnValue({ mutate, isPending: false } as unknown as ReturnType<
      typeof useLogin
    >)

    renderPage()
    await userEvent.type(screen.getByLabelText('Email'), 'user@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'secret')
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }))

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/', { replace: true })
    })
  })

  it('navigates to /change-password when forcePasswordChange is true', async () => {
    const mutate = vi.fn(
      (
        _vars: unknown,
        opts?: { onSuccess?: (data: { accessToken: string; forcePasswordChange: boolean }) => void }
      ) => {
        opts?.onSuccess?.({ accessToken: 'tok', forcePasswordChange: true })
      }
    )
    mockUseLogin.mockReturnValue({ mutate, isPending: false } as unknown as ReturnType<
      typeof useLogin
    >)

    renderPage()
    await userEvent.type(screen.getByLabelText('Email'), 'user@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'secret')
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }))

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/change-password', { replace: true })
    })
  })

  it('maps 400 field errors to the corresponding form fields', async () => {
    const mutate = vi.fn((_vars: unknown, opts?: { onError?: (err: unknown) => void }) => {
      opts?.onError?.(
        new ApiClientError('Validation failed', 400, [
          { code: 'INVALID', message: 'Email not found', field: 'email' },
        ])
      )
    })
    mockUseLogin.mockReturnValue({ mutate, isPending: false } as unknown as ReturnType<
      typeof useLogin
    >)

    renderPage()
    await userEvent.type(screen.getByLabelText('Email'), 'wrong@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'secret')
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }))

    await waitFor(() => {
      expect(screen.getByText('Email not found')).toBeInTheDocument()
    })
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('shows InlineBanner for non-field auth errors', async () => {
    const mutate = vi.fn((_vars: unknown, opts?: { onError?: (err: unknown) => void }) => {
      opts?.onError?.(
        new ApiClientError('Unauthorized', 401, [
          { code: 'INVALID_CREDENTIALS', message: 'Invalid email or password.', field: null },
        ])
      )
    })
    mockUseLogin.mockReturnValue({ mutate, isPending: false } as unknown as ReturnType<
      typeof useLogin
    >)

    renderPage()
    await userEvent.type(screen.getByLabelText('Email'), 'user@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'wrong')
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }))

    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument()
      expect(screen.getByText('Invalid email or password.')).toBeInTheDocument()
    })
  })

  it('renders the heading and field labels in Spanish when the UI locale is es', async () => {
    await i18n.changeLanguage('es')
    mockUseLogin.mockReturnValue({ mutate: vi.fn(), isPending: false } as unknown as ReturnType<
      typeof useLogin
    >)

    renderPage()

    expect(screen.getByRole('heading', { name: 'Iniciar sesión' })).toBeInTheDocument()
    expect(screen.getByLabelText('Correo electrónico')).toBeInTheDocument()
    expect(screen.getByLabelText('Contraseña')).toBeInTheDocument()
    expect(screen.queryByLabelText('Email')).not.toBeInTheDocument()
  })

  it('disables the submit button with a spinner while the mutation is pending', () => {
    mockUseLogin.mockReturnValue({ mutate: vi.fn(), isPending: true } as unknown as ReturnType<
      typeof useLogin
    >)

    renderPage()
    const button = screen.getByRole('button', { name: /sign in/i })
    expect(button).toBeDisabled()
    expect(button).toHaveAttribute('aria-disabled', 'true')
  })
})
