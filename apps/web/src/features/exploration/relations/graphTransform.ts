import { MarkerType, type Edge, type Node } from '@xyflow/react'
import type { EntitySummary } from '@/types/worldstate'
import type { EdgeData, NodeData, Relation } from '@/types/relation'

export interface GraphModel {
  nodes: Node<NodeData>[]
  edges: Edge<EdgeData>[]
  /** Relations that cannot be drawn because an endpoint is unresolved or absent from the graph. */
  unresolved: Relation[]
}

const COLUMNS = 4
const COL_GAP = 280
const ROW_GAP = 160
/** Vertical gap inserted between the actor band and the space band so the two types read as groups. */
const BAND_GAP = 96

/** Arrowhead applied to every drawn edge so a relation's direction (source → target) is legible. */
const EDGE_MARKER = {
  type: MarkerType.ArrowClosed,
  width: 18,
  height: 18,
  color: '#94a3b8', // slate-400
} as const

/**
 * Lays a list of entities on a deterministic {@link COLUMNS}-wide grid, starting at vertical offset
 * `yOffset`. Returns both the nodes and the y of the row below the band so the next band can stack.
 */
function layoutBand(
  entities: EntitySummary[],
  type: 'actor' | 'space',
  yOffset: number
): { nodes: Node<NodeData>[]; nextY: number } {
  const nodes = entities.map((entity, index) => ({
    id: entity.entityId,
    type: 'relationNode',
    position: {
      x: (index % COLUMNS) * COL_GAP,
      y: yOffset + Math.floor(index / COLUMNS) * ROW_GAP,
    },
    data: { entityId: entity.entityId, entityType: type, name: entity.name },
  }))
  const rows = Math.ceil(entities.length / COLUMNS)
  return { nodes, nextY: yOffset + rows * ROW_GAP }
}

/**
 * Pure transform from the campaign's actors, spaces, and relations into a React Flow graph model
 * (F4.3.9, D-030). Actors fill a top band and spaces a separate lower band so the two entity types
 * read as visual groups; a relation becomes an edge only when both endpoints resolve to a node that
 * exists in the graph. Relations with a null or dangling endpoint are returned in
 * {@link GraphModel.unresolved} for the accessible list — they are never silently dropped.
 */
export function graphTransform(
  actors: EntitySummary[],
  spaces: EntitySummary[],
  relations: Relation[]
): GraphModel {
  const actorBand = layoutBand(actors, 'actor', 0)
  const spaceBand = layoutBand(spaces, 'space', actorBand.nextY + BAND_GAP)
  const nodes: Node<NodeData>[] = [...actorBand.nodes, ...spaceBand.nodes]

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
        markerEnd: EDGE_MARKER,
        data: { relationId: relation.relationId, label: relation.kind ?? relation.name },
      })
    } else {
      unresolved.push(relation)
    }
  }

  return { nodes, edges, unresolved }
}
