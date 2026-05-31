import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { axe } from 'vitest-axe'
import { DiffReviewPage } from './DiffReviewPage'
import { useSessionDiff } from '@/api/sessions'
import type {
  ConflictCard,
  DiffPayload,
  ExistingDiffCard,
  UncertainDiffCard,
} from '@/types/session'

vi.mock('@/api/sessions')

const mockUseSessionDiff = vi.mocked(useSessionDiff)

type DiffResult = Partial<ReturnType<typeof useSessionDiff>>

function mockDiff(result: DiffResult) {
  mockUseSessionDiff.mockReturnValue(result as ReturnType<typeof useSessionDiff>)
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

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/campaigns/c1/sessions/s1/diff']}>
      <Routes>
        <Route
          path="/campaigns/:campaignId/sessions/:sessionId/diff"
          element={<DiffReviewPage />}
        />
      </Routes>
    </MemoryRouter>
  )
}

beforeEach(() => {
  vi.clearAllMocks()
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

  it('has no accessibility violations on the loaded screen', async () => {
    mockDiff({ data: fullDiff({ detectedConflicts: [] }), isLoading: false, isError: false })

    const { container } = renderPage()

    expect(await axe(container)).toHaveNoViolations()
  })
})
