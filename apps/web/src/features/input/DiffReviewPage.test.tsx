import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClientProvider } from '@tanstack/react-query'
import { axe } from 'vitest-axe'
import { DiffReviewPage } from './DiffReviewPage'
import { useSessionDiff, useCommitSession, useDiscardSession } from '@/api/sessions'
import { ApiClientError } from '@/api/client'
import { useCampaignStore } from '@/store/campaignStore'
import { createTestQueryClient } from '@/test/createTestQueryClient'
import type { CampaignRole } from '@/types/campaign'
import type {
  ConflictCard,
  DiffPayload,
  ExistingDiffCard,
  UncertainDiffCard,
} from '@/types/session'

vi.mock('@/api/sessions')

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>()
  return { ...actual, useNavigate: () => mockNavigate }
})

const mockUseSessionDiff = vi.mocked(useSessionDiff)
const mockUseCommitSession = vi.mocked(useCommitSession)
const mockUseDiscardSession = vi.mocked(useDiscardSession)

type DiffResult = Partial<ReturnType<typeof useSessionDiff>>
type MutateOpts = { onSuccess?: () => void; onError?: (err: unknown) => void }

function mockDiff(result: DiffResult) {
  mockUseSessionDiff.mockReturnValue(result as ReturnType<typeof useSessionDiff>)
}

function mockCommit(impl: (vars: unknown, opts?: MutateOpts) => void, isPending = false) {
  mockUseCommitSession.mockReturnValue({
    mutate: vi.fn(impl),
    isPending,
  } as unknown as ReturnType<typeof useCommitSession>)
}

function mockDiscard(impl: (vars: unknown, opts?: MutateOpts) => void, isPending = false) {
  mockUseDiscardSession.mockReturnValue({
    mutate: vi.fn(impl),
    isPending,
  } as unknown as ReturnType<typeof useDiscardSession>)
}

const existing: ExistingDiffCard = {
  cardId: 'e1',
  cardType: 'EXISTING',
  entityId: 'ent-1',
  entityType: 'actor',
  name: 'Strahd',
  changedFields: { title: 'Count' },
}

const uncertain: UncertainDiffCard = {
  cardId: 'u1',
  cardType: 'UNCERTAIN',
  entityType: 'actor',
  extractedMention: 'the old woman',
  candidateEntityId: 'ent-eva',
  candidateEntityName: 'Madam Eva',
}

const conflict: ConflictCard = {
  conflictId: 'k1',
  entityId: 'ent-1',
  entityType: 'actor',
  description: 'Allegiance contradiction',
  extractedFact: 'Ally',
  existingFact: 'Enemy',
}

function fullDiff(overrides: Partial<DiffPayload> = {}): DiffPayload {
  return {
    narrativeSummaryHeader: 'The heroes entered Barovia.',
    actors: [existing, uncertain],
    spaces: [],
    events: [],
    relations: [],
    detectedConflicts: [conflict],
    ...overrides,
  }
}

function renderPage(role: CampaignRole | null = 'editor') {
  if (role) useCampaignStore.getState().setCampaign('c1', role)
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <MemoryRouter initialEntries={['/campaigns/c1/sessions/s1/diff']}>
        <Routes>
          <Route
            path="/campaigns/:campaignId/sessions/:sessionId/diff"
            element={<DiffReviewPage />}
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  useCampaignStore.getState().clearCampaign()
  mockCommit(() => {})
  mockDiscard(() => {})
})

describe('DiffReviewPage', () => {
  it('shows a skeleton while loading', () => {
    mockDiff({ data: undefined, isLoading: true, isError: false })

    renderPage()

    expect(screen.getByRole('status', { name: /loading session diff/i })).toBeInTheDocument()
  })

  it('shows an error banner when the diff fails to load', () => {
    mockDiff({ data: undefined, isLoading: false, isError: true })

    renderPage()

    expect(screen.getByRole('alert')).toHaveTextContent(/couldn't load this session's review/i)
  })

  it('renders the narrative header and the category cards on success', () => {
    mockDiff({ data: fullDiff(), isLoading: false, isError: false })

    renderPage()

    expect(screen.getByText('The heroes entered Barovia.')).toBeInTheDocument()
    expect(screen.getByText('Strahd')).toBeInTheDocument()
    expect(screen.getByRole('region', { name: /actors/i })).toBeInTheDocument()
  })

  it('keeps the commit button disabled until the UNCERTAIN card and conflict are resolved', async () => {
    mockDiff({ data: fullDiff(), isLoading: false, isError: false })

    renderPage()

    const commit = screen.getByRole('button', { name: /commit to world state/i })
    expect(commit).toBeDisabled()
    expect(screen.getByText('2 items require your decision')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('radio', { name: /same entity/i }))
    await userEvent.click(screen.getByRole('checkbox', { name: /acknowledge this conflict/i }))

    expect(commit).toBeEnabled()
  })

  it('disables commit with a no-entities note when the diff has only UNCERTAIN cards', async () => {
    mockDiff({
      data: fullDiff({ actors: [uncertain], detectedConflicts: [] }),
      isLoading: false,
      isError: false,
    })

    renderPage()

    await userEvent.click(screen.getByRole('radio', { name: /same entity/i }))

    expect(screen.getByRole('button', { name: /commit to world state/i })).toBeDisabled()
    expect(screen.getByText('No entities to commit.')).toBeInTheDocument()
  })

  it('opens the edit overlay and records an edit decision on save', async () => {
    mockDiff({
      data: fullDiff({ detectedConflicts: [], actors: [existing] }),
      isLoading: false,
      isError: false,
    })

    renderPage()

    await userEvent.click(screen.getByRole('button', { name: 'Edit' }))
    const input = await screen.findByLabelText('title')
    await userEvent.clear(input)
    await userEvent.type(input, 'Lord')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    // Overlay closed after save
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('commits and navigates to the campaign home on success', async () => {
    mockDiff({
      data: fullDiff({ actors: [existing], detectedConflicts: [] }),
      isLoading: false,
      isError: false,
    })
    mockCommit((_payload, opts) => opts?.onSuccess?.())

    renderPage()

    await userEvent.click(screen.getByRole('button', { name: /commit to world state/i }))

    await waitFor(() =>
      expect(mockNavigate).toHaveBeenCalledWith('/campaigns/c1', { replace: true })
    )
  })

  it('treats a 422 commit error as a UI bug: logs it and shows a generic banner', async () => {
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    mockDiff({
      data: fullDiff({ actors: [existing], detectedConflicts: [] }),
      isLoading: false,
      isError: false,
    })
    mockCommit((_payload, opts) =>
      opts?.onError?.(
        new ApiClientError('uncertain', 422, [
          { code: 'UNCERTAIN_ENTITIES_PRESENT', message: 'Resolve first', field: null },
        ])
      )
    )

    renderPage()

    await userEvent.click(screen.getByRole('button', { name: /commit to world state/i }))

    expect(
      await screen.findByText(/something went wrong committing this review/i)
    ).toBeInTheDocument()
    expect(errorSpy).toHaveBeenCalled()
    expect(mockNavigate).not.toHaveBeenCalled()
    errorSpy.mockRestore()
  })

  it('hides the discard button for a non-GM role', () => {
    mockDiff({ data: fullDiff(), isLoading: false, isError: false })

    renderPage('editor')

    expect(screen.queryByRole('button', { name: /discard draft/i })).not.toBeInTheDocument()
  })

  it('shows the discard button for a GM and discards then navigates to a new session', async () => {
    mockDiff({ data: fullDiff(), isLoading: false, isError: false })
    mockDiscard((_vars, opts) => opts?.onSuccess?.())

    renderPage('gm')

    await userEvent.click(screen.getByRole('button', { name: /discard draft/i }))
    const dialog = screen.getByRole('dialog')
    await userEvent.click(within(dialog).getByRole('button', { name: /discard draft/i }))

    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/campaigns/c1/sessions/new'))
  })

  it('adds an entity via the overlay and renders it as a card', async () => {
    mockDiff({
      data: fullDiff({ actors: [existing], detectedConflicts: [] }),
      isLoading: false,
      isError: false,
    })

    renderPage()

    await userEvent.click(screen.getByRole('button', { name: 'Add entity' }))
    const dialog = screen.getByRole('dialog', { name: /add entity/i })
    await userEvent.type(within(dialog).getByLabelText('Name'), 'Madam Eva')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add entity' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByRole('region', { name: /added/i })).toBeInTheDocument()
    expect(screen.getByText('Madam Eva')).toBeInTheDocument()
  })

  it('removes a previously added entity', async () => {
    mockDiff({
      data: fullDiff({ actors: [existing], detectedConflicts: [] }),
      isLoading: false,
      isError: false,
    })

    renderPage()

    await userEvent.click(screen.getByRole('button', { name: 'Add entity' }))
    const dialog = screen.getByRole('dialog', { name: /add entity/i })
    await userEvent.type(within(dialog).getByLabelText('Name'), 'Madam Eva')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add entity' }))

    await userEvent.click(screen.getByRole('button', { name: 'Remove' }))

    expect(screen.queryByText('Madam Eva')).not.toBeInTheDocument()
  })

  it('includes added entities in the committed payload', async () => {
    let captured: { addedEntities?: unknown } | undefined
    mockDiff({
      data: fullDiff({ actors: [existing], detectedConflicts: [] }),
      isLoading: false,
      isError: false,
    })
    mockCommit((payload, opts) => {
      captured = payload as { addedEntities?: unknown }
      opts?.onSuccess?.()
    })

    renderPage()

    await userEvent.click(screen.getByRole('button', { name: 'Add entity' }))
    const dialog = screen.getByRole('dialog', { name: /add entity/i })
    await userEvent.type(within(dialog).getByLabelText('Name'), 'Madam Eva')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add entity' }))

    await userEvent.click(screen.getByRole('button', { name: /commit to world state/i }))

    expect(captured?.addedEntities).toEqual([
      { entityType: 'actor', name: 'Madam Eva', fields: {} },
    ])
  })

  it('has no accessibility violations on the loaded screen', async () => {
    mockDiff({ data: fullDiff({ detectedConflicts: [] }), isLoading: false, isError: false })

    const { container } = renderPage()

    expect(await axe(container)).toHaveNoViolations()
  })
})
