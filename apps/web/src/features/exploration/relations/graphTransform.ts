import type { Edge, Node } from '@xyflow/react'
import type { EntitySummary } from '@/types/worldstate'
import type { EdgeData, NodeData, Relation } from '@/types/relation'

export interface GraphModel {
  nodes: Node<NodeData>[]
  edges: Edge<EdgeData>[]
  /** Relations that cannot be drawn because an endpoint is unresolved or absent from the graph. */
  unresolved: Relation[]
}

const COLUMNS = 4
const COL_GAP = 220
const ROW_GAP = 140

/**
 * Pure transform from the campaign's actors, spaces, and relations into a React Flow graph model
 * (F4.3.9, D-030). Actors and spaces become nodes laid out on a deterministic grid; a relation
 * becomes an edge only when both endpoints resolve to a node that exists in the graph. Relations
 * with a null or dangling endpoint are returned in {@link GraphModel.unresolved} for the accessible
 * list — they are never silently dropped.
 */
export function graphTransform(
  actors: EntitySummary[],
  spaces: EntitySummary[],
  relations: Relation[]
): GraphModel {
  const entities = [...actors, ...spaces]
  const nodes: Node<NodeData>[] = entities.map((entity, index) => ({
    id: entity.entityId,
    type: 'relationNode',
    position: { x: (index % COLUMNS) * COL_GAP, y: Math.floor(index / COLUMNS) * ROW_GAP },
    data: {
      entityId: entity.entityId,
      entityType: entity.entityType === 'space' ? 'space' : 'actor',
      name: entity.name,
    },
  }))

  const nodeIds = new Set(nodes.map((node) => node.id))
  const edges: Edge<EdgeData>[] = []
  const unresolved: Relation[] = []

  for (const relation of relations) {
    const { sourceEntityId, targetEntityId } = relation
    if (
      sourceEntityId &&
      targetEntityId &&
      nodeIds.has(sourceEntityId) &&
      nodeIds.has(targetEntityId)
    ) {
      edges.push({
        id: relation.relationId,
        source: sourceEntityId,
        target: targetEntityId,
        type: 'relationEdge',
        data: { relationId: relation.relationId, label: relation.kind ?? relation.name },
      })
    } else {
      unresolved.push(relation)
    }
  }

  return { nodes, edges, unresolved }
}
