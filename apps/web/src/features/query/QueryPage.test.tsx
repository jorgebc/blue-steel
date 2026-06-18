import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClientProvider } from '@tanstack/react-query'
import { axe } from 'vitest-axe'
import { QueryPage } from './QueryPage'
import { ApiClientError } from '@/api/client'
import { useSubmitQuery, useQueryUsage, useQueryHistory } from '@/api/queries'
import { useCampaignStore } from '@/store/campaignStore'
import { createTestQueryClient } from '@/test/createTestQueryClient'
import type { QueryResponse, QueryUsage } from '@/types/query'

vi.mock('@/api/queries', async () => {
  const actual = await vi.importActual<typeof import('@/api/queries')>('@/api/queries')
  return { ...actual, useSubmitQuery: vi.fn(), useQueryUsage: vi.fn(), useQueryHistory: vi.fn() }
})
const mockUseSubmitQuery = vi.mocked(useSubmitQuery)
const mockUseQueryUsage = vi.mocked(useQueryUsage)
const mockUseQueryHistory = vi.mocked(useQueryHistory)

const usage: QueryUsage = {
  consumedUsd: 0.25,
  capUsd: 1.0,
  requestsRemaining: 8,
  maxRequests: 10,
  windowSeconds: 60,
}

const answer: QueryResponse = {
  answer: 'Aldric fled north after the battle.',
  citations: [
    { sessionId: 's1', sequenceNumber: 3, snippet: 'Aldric was last seen heading north.' },
  ],
}

type MutateOpts = {
  onSuccess?: (data: QueryResponse) => void
  onError?: (err: unknown) => void
  onSettled?: () => void
}

/** Stubs useSubmitQuery so mutate immediately invokes the configured outcome. */
function stubMutation(opts: {
  isPending?: boolean
  resolveWith?: QueryResponse
  rejectWith?: unknown
}) {
  const mutate = vi.fn((_question: string, callbacks?: MutateOpts) => {
    if (opts.rejectWith !== undefined) callbacks?.onError?.(opts.rejectWith)
    else if (opts.resolveWith) callbacks?.onSuccess?.(opts.resolveWith)
    callbacks?.onSettled?.()
  })
  mockUseSubmitQuery.mockReturnValue({
    mutate,
    isPending: opts.isPending ?? false,
  } as unknown as ReturnType<typeof useSubmitQuery>)
  return mutate
}

function renderPage() {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <MemoryRouter>
        <QueryPage />
      </MemoryRouter>
    </QueryClientProvider>
  )
}

async function ask(text = 'Where did Aldric go?') {
  await userEvent.type(screen.getByRole('textbox'), text)
  await userEvent.click(screen.getByRole('button', { name: /ask/i }))
}

beforeEach(() => {
  vi.clearAllMocks()
  useCampaignStore.setState({ activeCampaignId: 'c1', activeRole: 'player' })
  stubMutation({ resolveWith: answer })
  mockUseQueryUsage.mockReturnValue({ data: usage } as unknown as ReturnType<typeof useQueryUsage>)
  mockUseQueryHistory.mockReturnValue({
    data: { items: [], page: 0, size: 20, totalCount: 0 },
    isLoading: false,
    isError: false,
  } as unknown as ReturnType<typeof useQueryHistory>)
})

describe('QueryPage', () => {
  it('renders the heading and question form', () => {
    renderPage()
    expect(screen.getByRole('heading', { name: /ask the world/i })).toBeInTheDocument()
    expect(screen.getByRole('textbox')).toBeInTheDocument()
  })

  it('renders the answer and citations after a successful query', async () => {
    renderPage()
    await ask()

    expect(screen.getByText('Aldric fled north after the battle.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /session 3/i })).toHaveAttribute(
      'href',
      '/campaigns/c1/sessions/s1'
    )
  })

  it('shows a rephrasing suggestion when the query times out (504)', async () => {
    stubMutation({ rejectWith: new ApiClientError('timeout', 504, []) })
    renderPage()
    await ask()

    expect(screen.getByRole('alert')).toHaveTextContent(/took too long/i)
    expect(screen.getByRole('alert')).toHaveTextContent(/rephras/i)
  })

  it('shows a generic error banner for non-timeout failures', async () => {
    stubMutation({ rejectWith: new ApiClientError('boom', 500, []) })
    renderPage()
    await ask()

    expect(screen.getByRole('alert')).toHaveTextContent(/something went wrong/i)
  })

  it('shows a rate-limit message when the caller is throttled (429)', async () => {
    stubMutation({
      rejectWith: new ApiClientError('rate limited', 429, [
        { code: 'QUERY_RATE_LIMITED', message: 'Too many queries.', field: null },
      ]),
    })
    renderPage()
    await ask()

    expect(screen.getByRole('alert')).toHaveTextContent(/too quickly/i)
  })

  it('shows a cost-cap message when the daily budget is exhausted (503)', async () => {
    stubMutation({
      rejectWith: new ApiClientError('cost cap', 503, [
        { code: 'QUERY_COST_CAP', message: 'Budget reached.', field: null },
      ]),
    })
    renderPage()
    await ask()

    expect(screen.getByRole('alert')).toHaveTextContent(/daily question limit/i)
  })

  it('surfaces the backend message for other coded failures', async () => {
    stubMutation({
      rejectWith: new ApiClientError('unparseable', 502, [
        {
          code: 'QUERY_ANSWER_UNPARSEABLE',
          message: 'The answer service returned an unreadable response.',
          field: null,
        },
      ]),
    })
    renderPage()
    await ask()

    expect(screen.getByRole('alert')).toHaveTextContent(/unreadable response/i)
  })

  it('renders the loading skeleton while a query is pending', () => {
    stubMutation({ isPending: true })
    renderPage()

    expect(screen.getByRole('status', { name: /searching the world state/i })).toBeInTheDocument()
  })

  it('clears a previous answer when the next query fails', async () => {
    const mutate = stubMutation({ resolveWith: answer })
    renderPage()
    await ask()
    expect(screen.getByText('Aldric fled north after the battle.')).toBeInTheDocument()

    // Reconfigure the same mutate stub to reject on the next call.
    mutate.mockImplementation((_q: string, callbacks?: MutateOpts) => {
      callbacks?.onError?.(new ApiClientError('boom', 500, []))
    })
    await ask('Another question?')

    expect(screen.queryByText('Aldric fled north after the battle.')).not.toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent(/something went wrong/i)
  })

  it('shows an empty-state prompt before the first query is submitted', () => {
    renderPage()
    expect(
      screen.getByText(/submit a question to see the answer and its sources here/i)
    ).toBeInTheDocument()
  })

  it('hides the empty-state prompt when an error banner is visible', async () => {
    stubMutation({ rejectWith: new ApiClientError('timeout', 504, []) })
    renderPage()
    await ask()

    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(
      screen.queryByText(/submit a question to see the answer and its sources here/i)
    ).not.toBeInTheDocument()
  })

  it('hides the empty-state prompt once an answer is displayed', async () => {
    renderPage()
    await ask()

    expect(
      screen.queryByText(/submit a question to see the answer and its sources here/i)
    ).not.toBeInTheDocument()
    expect(screen.getByText('Aldric fled north after the battle.')).toBeInTheDocument()
  })

  it('moves focus to the answer heading when a result renders', async () => {
    renderPage()
    await ask()

    expect(document.activeElement).toBe(document.getElementById('answer-heading'))
  })

  it('shows the shared free-tier moderation notice with live budget usage', () => {
    renderPage()
    expect(screen.getByRole('note')).toHaveTextContent(/free ai tier/i)
    expect(screen.getByRole('note')).toHaveTextContent(/25% used/i)
  })

  it('refreshes the usage figure after a query settles', async () => {
    const client = createTestQueryClient()
    const invalidate = vi.spyOn(client, 'invalidateQueries')
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter>
          <QueryPage />
        </MemoryRouter>
      </QueryClientProvider>
    )
    await ask()

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['query-usage', 'c1'] })
  })

  it('mounts the question history panel alongside the live-answer flow', () => {
    renderPage()
    expect(screen.getByRole('heading', { name: /question history/i })).toBeInTheDocument()
    expect(screen.getByText(/no questions have been asked yet/i)).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = renderPage()
    expect(await axe(container)).toHaveNoViolations()
  })
})
