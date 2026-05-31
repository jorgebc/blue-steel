import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { axe } from 'vitest-axe'
import { SubmitSessionPage } from './SubmitSessionPage'
import { useSubmitSession } from '@/api/sessions'
import { ApiClientError } from '@/api/client'
import { useCampaignStore } from '@/store/campaignStore'
import type { CampaignRole } from '@/types/campaign'

// Keep extractExistingSessionId real; only stub the mutation hook.
vi.mock('@/api/sessions', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/sessions')>()
  return { ...actual, useSubmitSession: vi.fn() }
})

vi.mock('./ProcessingStatusView', () => ({
  ProcessingStatusView: ({ sessionId }: { sessionId: string }) => (
    <div data-testid="processing-view">Processing {sessionId}</div>
  ),
}))

const mockUseSubmitSession = vi.mocked(useSubmitSession)

type MutateOpts = {
  onSuccess?: (data: { sessionId: string; status: string }) => void
  onError?: (err: unknown) => void
}

/** Stubs the mutation hook, invoking the supplied callback when `mutate` runs. */
function mockSubmit(impl: (vars: unknown, opts?: MutateOpts) => void, isPending = false) {
  mockUseSubmitSession.mockReturnValue({
    mutate: vi.fn(impl),
    isPending,
  } as unknown as ReturnType<typeof useSubmitSession>)
}

function renderPage(role: CampaignRole = 'editor') {
  useCampaignStore.getState().setCampaign('c1', role)
  return render(
    <MemoryRouter initialEntries={['/campaigns/c1/sessions/new']}>
      <Routes>
        <Route path="/campaigns/:campaignId/sessions/new" element={<SubmitSessionPage />} />
        <Route path="/campaigns/:campaignId" element={<div>Campaign home</div>} />
      </Routes>
    </MemoryRouter>
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  useCampaignStore.getState().clearCampaign()
})

describe('SubmitSessionPage', () => {
  it('redirects a player to campaign home', () => {
    mockSubmit(() => {})
    renderPage('player')

    expect(screen.getByText('Campaign home')).toBeInTheDocument()
    expect(screen.queryByLabelText('Session summary')).not.toBeInTheDocument()
  })

  it('hands off to the processing view on a successful submit', async () => {
    mockSubmit((_vars, opts) => opts?.onSuccess?.({ sessionId: 's9', status: 'PENDING' }))
    renderPage('editor')

    await userEvent.type(screen.getByLabelText('Session summary'), 'We fought a lich.')
    await userEvent.click(screen.getByRole('button', { name: /submit session/i }))

    await waitFor(() => expect(screen.getByTestId('processing-view')).toHaveTextContent('s9'))
  })

  it('maps a VALIDATION_ERROR to the summary field', async () => {
    mockSubmit((_vars, opts) =>
      opts?.onError?.(
        new ApiClientError('invalid', 400, [
          { code: 'VALIDATION_ERROR', message: 'Summary must not be blank', field: 'summaryText' },
        ])
      )
    )
    renderPage('editor')

    await userEvent.type(screen.getByLabelText('Session summary'), 'x')
    await userEvent.click(screen.getByRole('button', { name: /submit session/i }))

    await waitFor(() => expect(screen.getByText('Summary must not be blank')).toBeInTheDocument())
  })

  it('shows the SUMMARY_TOO_LARGE message verbatim in an error banner', async () => {
    mockSubmit((_vars, opts) =>
      opts?.onError?.(
        new ApiClientError('too large', 400, [
          {
            code: 'SUMMARY_TOO_LARGE',
            message: 'Summary exceeds the 8000 token limit.',
            field: null,
          },
        ])
      )
    )
    renderPage('editor')

    await userEvent.type(screen.getByLabelText('Session summary'), 'long text')
    await userEvent.click(screen.getByRole('button', { name: /submit session/i }))

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('Summary exceeds the 8000 token limit.')
    )
  })

  it('shows a resume link built from the ACTIVE_SESSION_EXISTS session id', async () => {
    const uuid = '3f8a1c2e-9b4d-4f6a-8c1e-2d3b4a5c6d7e'
    mockSubmit((_vars, opts) =>
      opts?.onError?.(
        new ApiClientError('conflict', 409, [
          { code: 'ACTIVE_SESSION_EXISTS', message: `Active session: ${uuid}`, field: null },
        ])
      )
    )
    renderPage('editor')

    await userEvent.type(screen.getByLabelText('Session summary'), 'another session')
    await userEvent.click(screen.getByRole('button', { name: /submit session/i }))

    const link = await screen.findByRole('link', { name: /resume your unfinished review/i })
    expect(link).toHaveAttribute('href', `/campaigns/c1/sessions/${uuid}/diff`)
  })

  it('disables the submit button with a spinner while the mutation is pending', () => {
    mockSubmit(() => {}, true)
    renderPage('editor')

    const button = screen.getByRole('button', { name: /submit session/i })
    expect(button).toBeDisabled()
    expect(button).toHaveAttribute('aria-disabled', 'true')
  })

  it('has no accessibility violations', async () => {
    mockSubmit(() => {})
    const { container } = renderPage('editor')

    expect(await axe(container)).toHaveNoViolations()
  })
})
