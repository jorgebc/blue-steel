import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { axe } from 'vitest-axe'
import { RelationsPage } from './RelationsPage'
import { useAllEntities } from '@/api/worldstate'
import { useRelations } from '@/api/relations'
import type { EntitySummary } from '@/types/worldstate'
import type { Relation } from '@/types/relation'

// React Flow needs a measured DOM (ResizeObserver) it doesn't get in jsdom; stub it so the page's
// own logic (skeleton, error, accessible list) is what we exercise.
vi.mock('@xyflow/react', () => ({
  ReactFlow: ({
    children,
    'aria-label': label,
  }: {
    children?: React.ReactNode
    'aria-label'?: string
  }) => <div aria-label={label}>{children}</div>,
  Background: () => null,
  Controls: () => null,
}))
vi.mock('@/api/worldstate', () => ({ useAllEntities: vi.fn() }))
vi.mock('@/api/relations', () => ({ useRelations: vi.fn() }))
vi.mock('@/api/annotations', () => ({
  useAnnotations: () => ({ data: [], isLoading: false, isError: false }),
  usePostAnnotation: () => ({ mutate: vi.fn(), isPending: false }),
  useDeleteAnnotation: () => ({ mutate: vi.fn(), isPending: false }),
}))

const mockUseAllEntities = vi.mocked(useAllEntities)
const mockUseRelations = vi.mocked(useRelations)

function actor(id: string, name: string): EntitySummary {
  return {
    entityId: id,
    entityType: 'actor',
    name,
    latestVersionNumber: 1,
    currentSnapshot: {},
    lastUpdatedSessionId: null,
    createdAt: '2026-01-01T00:00:00Z',
  }
}

const relation: Relation = {
  relationId: 'r1',
  name: 'Mira guides the party',
  kind: 'alliance',
  sourceEntityId: 'a1',
  sourceEntityType: 'actor',
  targetEntityId: 'a2',
  targetEntityType: 'actor',
  sessionId: 's1',
}

type AllEntitiesResult = ReturnType<typeof useAllEntities>
type RelationsResult = ReturnType<typeof useRelations>

function allEntitiesResult(over: Partial<AllEntitiesResult>): AllEntitiesResult {
  return {
    data: [],
    isLoading: false,
    isError: false,
    ...over,
  } as unknown as AllEntitiesResult
}

function relationsResult(over: Partial<RelationsResult>): RelationsResult {
  return { data: [], isLoading: false, isError: false, ...over } as unknown as RelationsResult
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/campaigns/c1/explore/relations']}>
      <RelationsPage />
    </MemoryRouter>
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  mockUseAllEntities.mockImplementation((entityType) =>
    allEntitiesResult({
      data: entityType === 'actor' ? [actor('a1', 'Mira'), actor('a2', 'Aldric')] : [],
    })
  )
  mockUseRelations.mockReturnValue(relationsResult({ data: [relation] }))
})

describe('RelationsPage', () => {
  it('renders the graph canvas and the accessible relations list', () => {
    renderPage()

    expect(screen.getByLabelText('Relations graph')).toBeInTheDocument()
    const item = screen.getByRole('listitem')
    expect(item).toHaveTextContent('Mira')
    expect(item).toHaveTextContent('alliance')
    expect(item).toHaveTextContent('Aldric')
  })

  it('does not show the annotation panel until a relation is selected', () => {
    renderPage()
    expect(screen.queryByRole('region', { name: /annotations/i })).not.toBeInTheDocument()
    expect(screen.queryByText(/no annotations yet/i)).not.toBeInTheDocument()
  })

  it('opens the annotation thread for the selected relation', async () => {
    renderPage()

    await userEvent.click(screen.getByRole('button', { name: /show annotations/i }))

    expect(screen.getByRole('region', { name: 'Annotations' })).toBeInTheDocument()
    expect(screen.getByText(/no annotations yet/i)).toBeInTheDocument()
  })

  it('shows the skeleton while any query is loading', () => {
    mockUseRelations.mockReturnValue(relationsResult({ isLoading: true }))
    renderPage()

    expect(screen.getByRole('status', { name: /loading relations/i })).toBeInTheDocument()
  })

  it('shows an error banner when a query fails', () => {
    mockUseRelations.mockReturnValue(relationsResult({ isError: true }))
    renderPage()

    expect(screen.getByRole('alert')).toHaveTextContent(/could not load the relations graph/i)
  })

  it('has no accessibility violations', async () => {
    const { container } = renderPage()
    expect(await axe(container)).toHaveNoViolations()
  })
})
