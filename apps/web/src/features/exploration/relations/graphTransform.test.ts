import { describe, it, expect } from 'vitest'
import { graphTransform } from './graphTransform'
import type { EntitySummary } from '@/types/worldstate'
import type { Relation } from '@/types/relation'

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

function space(id: string, name: string): EntitySummary {
  return { ...actor(id, name), entityType: 'space' }
}

function relation(over: Partial<Relation>): Relation {
  return {
    relationId: 'r1',
    name: 'a bond',
    kind: null,
    sourceEntityId: null,
    sourceEntityType: null,
    targetEntityId: null,
    targetEntityType: null,
    sessionId: 's1',
    ...over,
  }
}

describe('graphTransform', () => {
  it('creates one node per actor and space carrying its type', () => {
    const { nodes } = graphTransform([actor('a1', 'Mira')], [space('s1', 'Thornwick')], [])

    expect(nodes).toHaveLength(2)
    expect(nodes[0]).toMatchObject({ id: 'a1', type: 'relationNode' })
    expect(nodes[0].data).toMatchObject({ entityType: 'actor', name: 'Mira' })
    expect(nodes[1].data).toMatchObject({ entityType: 'space', name: 'Thornwick' })
  })

  it('emits an edge when both endpoints resolve to known nodes', () => {
    const { edges, unresolved } = graphTransform(
      [actor('a1', 'Mira')],
      [space('s1', 'Thornwick')],
      [relation({ relationId: 'r1', kind: 'alliance', sourceEntityId: 'a1', targetEntityId: 's1' })]
    )

    expect(unresolved).toHaveLength(0)
    expect(edges).toHaveLength(1)
    expect(edges[0]).toMatchObject({ id: 'r1', source: 'a1', target: 's1', type: 'relationEdge' })
    expect(edges[0].data).toMatchObject({ relationId: 'r1', label: 'alliance' })
  })

  it('falls back to the relation name when kind is null', () => {
    const { edges } = graphTransform(
      [actor('a1', 'Mira'), actor('a2', 'Aldric')],
      [],
      [relation({ name: 'rivalry', kind: null, sourceEntityId: 'a1', targetEntityId: 'a2' })]
    )

    expect(edges[0].data?.label).toBe('rivalry')
  })

  it('treats a relation with a null endpoint as unresolved', () => {
    const { edges, unresolved } = graphTransform(
      [actor('a1', 'Mira')],
      [],
      [relation({ sourceEntityId: 'a1', targetEntityId: null })]
    )

    expect(edges).toHaveLength(0)
    expect(unresolved).toHaveLength(1)
  })

  it('treats a relation pointing at an absent node as unresolved', () => {
    const { edges, unresolved } = graphTransform(
      [actor('a1', 'Mira')],
      [],
      [relation({ sourceEntityId: 'a1', targetEntityId: 'ghost' })]
    )

    expect(edges).toHaveLength(0)
    expect(unresolved).toHaveLength(1)
  })
})
