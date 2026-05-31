import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { axe } from 'vitest-axe'
import { ProcessingStatusView } from './ProcessingStatusView'
import { useSessionStatus } from '@/api/sessions'
import type { SessionStatusResponse } from '@/types/session'

vi.mock('@/api/sessions')

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>()
  return { ...actual, useNavigate: () => mockNavigate }
})

const mockUseSessionStatus = vi.mocked(useSessionStatus)

type StatusResult = Partial<ReturnType<typeof useSessionStatus>>

function mockStatus(result: StatusResult) {
  mockUseSessionStatus.mockReturnValue(result as ReturnType<typeof useSessionStatus>)
}

function statusData(overrides: Partial<SessionStatusResponse>): SessionStatusResponse {
  return {
    sessionId: 's1',
    status: 'PROCESSING',
    failureReason: null,
    message: null,
    ...overrides,
  }
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('ProcessingStatusView', () => {
  it('renders the processing skeleton while the session is PROCESSING', () => {
    mockStatus({ data: statusData({ status: 'PROCESSING' }), isError: false })

    render(<ProcessingStatusView campaignId="c1" sessionId="s1" />)

    expect(screen.getByText('Processing your session…')).toBeInTheDocument()
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('navigates to the diff review when the session reaches DRAFT', async () => {
    mockStatus({ data: statusData({ status: 'DRAFT' }), isError: false })

    render(<ProcessingStatusView campaignId="c1" sessionId="s1" />)

    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/campaigns/c1/sessions/s1/diff'))
  })

  it('shows an error banner with the failure reason and message when FAILED', () => {
    mockStatus({
      data: statusData({
        status: 'FAILED',
        failureReason: 'PIPELINE_NOT_IMPLEMENTED',
        message: 'The extraction pipeline is not available yet.',
      }),
      isError: false,
    })

    render(<ProcessingStatusView campaignId="c1" sessionId="s1" />)

    const banner = screen.getByRole('alert')
    expect(banner).toHaveTextContent('PIPELINE_NOT_IMPLEMENTED')
    expect(banner).toHaveTextContent('The extraction pipeline is not available yet.')
  })

  it('shows an error banner when the status request fails', () => {
    mockStatus({ data: undefined, isError: true })

    render(<ProcessingStatusView campaignId="c1" sessionId="s1" />)

    expect(screen.getByRole('alert')).toHaveTextContent(/couldn't check your session/i)
  })

  it('has no accessibility violations', async () => {
    mockStatus({ data: statusData({ status: 'PROCESSING' }), isError: false })

    const { container } = render(<ProcessingStatusView campaignId="c1" sessionId="s1" />)

    expect(await axe(container)).toHaveNoViolations()
  })
})
