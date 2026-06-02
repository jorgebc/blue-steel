import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { axe } from 'vitest-axe'
import { EntityVersionHistory } from './EntityVersionHistory'
import type { EntityVersion } from '@/types/worldstate'

const versions: EntityVersion[] = [
  {
    versionId: 'v1',
    versionNumber: 1,
    sessionId: 's1',
    sessionSequenceNumber: 1,
    changedFields: {},
    fullSnapshot: { role: 'squire' },
    createdAt: '2026-01-01T09:00:00Z',
  },
  {
    versionId: 'v2',
    versionNumber: 2,
    sessionId: 's2',
    sessionSequenceNumber: 3,
    changedFields: { role: 'knight' },
    fullSnapshot: { role: 'knight' },
    createdAt: '2026-01-02T09:00:00Z',
  },
]

describe('EntityVersionHistory', () => {
  it('renders a row per version, newest first, with the originating session', () => {
    render(<EntityVersionHistory versions={versions} />)

    const items = screen.getAllByRole('listitem')
    expect(items).toHaveLength(2)
    expect(items[0]).toHaveTextContent('v2')
    expect(items[0]).toHaveTextContent('Session #3')
    expect(items[1]).toHaveTextContent('v1')
    expect(items[1]).toHaveTextContent('Session #1')
  })

  it('summarises changed fields and marks the initial version', () => {
    render(<EntityVersionHistory versions={versions} />)

    expect(screen.getByText('Changed: role')).toBeInTheDocument()
    expect(screen.getByText('Initial version')).toBeInTheDocument()
  })

  it('shows an empty state when there are no versions', () => {
    render(<EntityVersionHistory versions={[]} />)

    expect(screen.getByText(/no version history yet/i)).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = render(<EntityVersionHistory versions={versions} />)
    expect(await axe(container)).toHaveNoViolations()
  })
})
