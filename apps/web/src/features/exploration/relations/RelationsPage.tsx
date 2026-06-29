import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Background,
  BackgroundVariant,
  Controls,
  ReactFlow,
  useNodesState,
  type Edge,
  type EdgeMouseHandler,
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import { ChevronDown, ChevronRight } from 'lucide-react'
import type { EdgeData } from '@/types/relation'
import { useAllEntities } from '@/api/worldstate'
import { useRelations } from '@/api/relations'
import { AnnotationThread } from '@/components/domain/AnnotationThread'
import { InlineBanner } from '@/components/domain/InlineBanner'
import { graphTransform } from './graphTransform'
import { RelationNode } from './RelationNode'
import { RelationEdge } from './RelationEdge'
import { RelationsGraphSkeleton } from './RelationsGraphSkeleton'
import { RelationsList } from './RelationsList'

// Defined at module scope so React Flow never sees a fresh object across renders.
const nodeTypes = { relationNode: RelationNode }
const edgeTypes = { relationEdge: RelationEdge }

/**
 * Exploration → Relations: a read-only React Flow graph of the campaign's actors and spaces (nodes)
 * connected by relations (edges), with an accessible relations list alongside (D-030, D-010, D-009).
 * Node positions are derived deterministically; world state is never edited from the graph.
 */
export function RelationsPage() {
  const navigate = useNavigate()
  const actorsQuery = useAllEntities('actor')
  const spacesQuery = useAllEntities('space')
  const relationsQuery = useRelations()
  const [selectedRelationId, setSelectedRelationId] = useState<string | null>(null)
  const [listOpen, setListOpen] = useState(false)

  const actors = useMemo(() => actorsQuery.data ?? [], [actorsQuery.data])
  const spaces = useMemo(() => spacesQuery.data ?? [], [spacesQuery.data])
  const relations = useMemo(() => relationsQuery.data ?? [], [relationsQuery.data])

  const { nodes, edges } = useMemo(
    () => graphTransform(actors, spaces, relations),
    [actors, spaces, relations]
  )

  // Node positions live in React Flow state so the user can drag nodes around (view-only — never a
  // world-state write, D-010). Re-seed from the deterministic layout whenever the data changes.
  const [rfNodes, setRfNodes, onNodesChange] = useNodesState(nodes)
  useEffect(() => setRfNodes(nodes), [nodes, setRfNodes])

  // Endpoints of the selected relation, so the canvas can mirror the list's selection.
  const highlightedNodeIds = useMemo(() => {
    const relation = relations.find((r) => r.relationId === selectedRelationId)
    const ids = new Set<string>()
    if (relation?.sourceEntityId) ids.add(relation.sourceEntityId)
    if (relation?.targetEntityId) ids.add(relation.targetEntityId)
    return ids
  }, [relations, selectedRelationId])

  const displayNodes = useMemo(
    () =>
      rfNodes.map((node) =>
        highlightedNodeIds.has(node.id)
          ? { ...node, data: { ...node.data, highlighted: true } }
          : node
      ),
    [rfNodes, highlightedNodeIds]
  )

  const displayEdges = useMemo(
    () =>
      edges.map((edge) =>
        edge.id === selectedRelationId && edge.data
          ? { ...edge, data: { ...edge.data, selected: true } }
          : edge
      ),
    [edges, selectedRelationId]
  )

  const handleEdgeClick = useCallback<EdgeMouseHandler<Edge<EdgeData>>>(
    (_, edge) => setSelectedRelationId(edge.id),
    []
  )

  const nameById = useMemo(() => {
    const map: Record<string, string> = {}
    for (const entity of [...actors, ...spaces]) {
      map[entity.entityId] = entity.name
    }
    return map
  }, [actors, spaces])

  const selectedRelationLabel = useMemo(() => {
    const relation = relations.find((r) => r.relationId === selectedRelationId)
    if (!relation) return ''
    const source = relation.sourceEntityId
      ? (nameById[relation.sourceEntityId] ?? 'Unknown')
      : 'Unknown'
    const target = relation.targetEntityId
      ? (nameById[relation.targetEntityId] ?? 'Unknown')
      : 'Unknown'
    return `${source} → ${relation.kind ?? relation.name} → ${target}`
  }, [relations, selectedRelationId, nameById])

  const isLoading = actorsQuery.isLoading || spacesQuery.isLoading || relationsQuery.isLoading
  const isError = actorsQuery.isError || spacesQuery.isError || relationsQuery.isError

  return (
    <section>
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-foreground">Relations</h1>
        <p className="text-sm text-muted-foreground">
          How this campaign&apos;s actors and spaces are connected.
        </p>
      </div>

      {isError && (
        <div className="mb-4">
          <InlineBanner
            variant="error"
            message="Could not load the relations graph. Please refresh the page."
            onDismiss={() => navigate(0)}
          />
        </div>
      )}

      {isLoading && <RelationsGraphSkeleton />}

      {!isLoading && !isError && (
        <div className="space-y-4">
          <div
            role="group"
            aria-label="Relations graph"
            className="h-[70vh] min-h-[520px] w-full overflow-hidden rounded-2xl border border-border bg-background"
          >
            <ReactFlow
              nodes={displayNodes}
              edges={displayEdges}
              nodeTypes={nodeTypes}
              edgeTypes={edgeTypes}
              colorMode="light"
              onNodesChange={onNodesChange}
              nodesDraggable
              nodesConnectable={false}
              elementsSelectable={false}
              edgesReconnectable={false}
              deleteKeyCode={null}
              onEdgeClick={handleEdgeClick}
              fitView
              fitViewOptions={{ padding: 0.2 }}
              minZoom={0.4}
              maxZoom={1.75}
              panOnScroll
            >
              <Background variant={BackgroundVariant.Dots} gap={16} size={1} />
              <Controls showInteractive={false} />
            </ReactFlow>
          </div>

          <div className="rounded-2xl border border-border bg-surface shadow-sm">
            <button
              type="button"
              aria-expanded={listOpen}
              aria-controls="relations-list-panel"
              onClick={() => setListOpen((open) => !open)}
              className="flex w-full items-center gap-2 rounded-2xl px-4 py-3 text-left text-sm font-medium text-foreground transition-colors duration-200 hover:bg-muted"
            >
              {listOpen ? (
                <ChevronDown className="h-4 w-4 text-muted-foreground" aria-hidden />
              ) : (
                <ChevronRight className="h-4 w-4 text-muted-foreground" aria-hidden />
              )}
              All relations
              <span className="text-muted-foreground">({relations.length})</span>
            </button>
            {listOpen && (
              <div id="relations-list-panel" className="px-4 pb-4">
                <RelationsList
                  relations={relations}
                  nameById={nameById}
                  selectedId={selectedRelationId}
                  onSelect={setSelectedRelationId}
                />
              </div>
            )}
          </div>
        </div>
      )}

      {!isLoading && !isError && selectedRelationId !== null && (
        <div className="mt-6 rounded-2xl border border-border bg-surface p-6 shadow-sm">
          <div className="mb-4">
            <h2 className="text-sm font-semibold text-foreground">{selectedRelationLabel}</h2>
          </div>
          <AnnotationThread entityType="relation" entityId={selectedRelationId} />
        </div>
      )}
    </section>
  )
}
