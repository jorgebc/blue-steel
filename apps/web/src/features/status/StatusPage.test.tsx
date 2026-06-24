import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'vitest-axe'
import { StatusPage } from './StatusPage'
import { useHealth } from '@/api/health'

vi.mock('@/api/health')

const mockUseHealth = vi.mocked(useHealth)

beforeEach(() => {
  vi.clearAllMocks()
})

describe('StatusPage', () => {
  it('has no accessibility violations when data is loaded', async () => {
    mockUseHealth.mockReturnValue({
      isLoading: false,
      data: { status: 'UP', db: 'UP' },
      error: null,
    } as unknown as ReturnType<typeof useHealth>)

    const { container } = render(<StatusPage />)
    expect(await axe(container)).toHaveNoViolations()
  })

  it('shows skeleton while loading and hides the heading', () => {
    mockUseHealth.mockReturnValue({
      isLoading: true,
      data: undefined,
      error: null,
    } as unknown as ReturnType<typeof useHealth>)

    render(<StatusPage />)
    expect(screen.getByRole('status', { name: 'Loading system status' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'System status' })).not.toBeInTheDocument()
  })

  it('renders API and database status values when data is available', () => {
    mockUseHealth.mockReturnValue({
      isLoading: false,
      data: { status: 'UP', db: 'UP' },
      error: null,
    } as unknown as ReturnType<typeof useHealth>)

    render(<StatusPage />)
    expect(screen.getByRole('heading', { name: 'System status' })).toBeInTheDocument()
    expect(screen.getAllByText('UP')).toHaveLength(2)
  })

  it('shows InlineBanner when the health fetch fails', async () => {
    mockUseHealth.mockReturnValue({
      isLoading: false,
      data: undefined,
      error: new Error('Network error'),
    } as unknown as ReturnType<typeof useHealth>)

    render(<StatusPage />)
    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(
      screen.getByText('Unable to reach the server. Please try again later.')
    ).toBeInTheDocument()
  })

  it('dismisses the error banner when the dismiss button is clicked', async () => {
    mockUseHealth.mockReturnValue({
      isLoading: false,
      data: undefined,
      error: new Error('Network error'),
    } as unknown as ReturnType<typeof useHealth>)

    render(<StatusPage />)
    expect(screen.getByRole('alert')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Dismiss' }))
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})
