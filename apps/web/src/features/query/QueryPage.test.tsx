import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { axe } from 'vitest-axe'
import { QueryPage } from './QueryPage'
import { ApiClientError } from '@/api/client'
import { useSubmitQuery } from '@/api/queries'
import { useCampaignStore } from '@/store/campaignStore'
import type { QueryResponse } from '@/types/query'

vi.mock('@/api/queries', () => ({ useSubmitQuery: vi.fn() }))
const mockUseSubmitQuery = vi.mocked(useSubmitQuery)

const answer: QueryResponse = {
  answer: 'Aldric fled north after the battle.',
  citations: [
    { sessionId: 's1', sequenceNumber: 3, snippet: 'Aldric was last seen heading north.' },
  ],
}

type MutateOpts = {
  onSuccess?: (data: QueryResponse) => void
  onError?: (err: unknown) => void
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
  })
  mockUseSubmitQuery.mockReturnValue({
    mutate,
    isPending: opts.isPending ?? false,
  } as unknown as ReturnType<typeof useSubmitQuery>)
  return mutate
}

function renderPage() {
  return render(
    <MemoryRouter>
      <QueryPage />
    </MemoryRouter>
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

  it('has no accessibility violations', async () => {
    const { container } = renderPage()
    expect(await axe(container)).toHaveNoViolations()
  })
})
