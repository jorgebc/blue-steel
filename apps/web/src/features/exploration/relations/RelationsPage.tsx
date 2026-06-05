import { useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { Background, Controls, ReactFlow } from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import { useAllEntities } from '@/api/worldstate'
import { useRelations } from '@/api/relations'
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

  const actors = useMemo(() => actorsQuery.data ?? [], [actorsQuery.data])
  const spaces = useMemo(() => spacesQuery.data ?? [], [spacesQuery.data])
  const relations = useMemo(() => relationsQuery.data ?? [], [relationsQuery.data])

  const { nodes, edges } = useMemo(
    () => graphTransform(actors, spaces, relations),
    [actors, spaces, relations]
  )

  const nameById = useMemo(() => {
    const map: Record<string, string> = {}
    for (const entity of [...actors, ...spaces]) {
      map[entity.entityId] = entity.name
    }
    return map
  }, [actors, spaces])

  const isLoading = actorsQuery.isLoading || spacesQuery.isLoading || relationsQuery.isLoading
  const isError = actorsQuery.isError || spacesQuery.isError || relationsQuery.isError

  return (
    <section>
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-slate-900">Relations</h1>
        <p className="text-sm text-slate-500">
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
        <div className="grid gap-6 lg:grid-cols-[2fr_1fr]">
          <div
            role="group"
            aria-label="Relations graph"
            className="h-[480px] w-full overflow-hidden rounded-2xl border border-slate-200 bg-white"
          >
            <ReactFlow
              nodes={nodes}
              edges={edges}
              nodeTypes={nodeTypes}
              edgeTypes={edgeTypes}
              nodesDraggable={false}
              nodesConnectable={false}
              elementsSelectable={false}
              edgesReconnectable={false}
              deleteKeyCode={null}
              fitView
            >
              <Background />
              <Controls showInteractive={false} />
            </ReactFlow>
          </div>

          <div>
            <h2 className="mb-2 text-sm font-medium text-slate-700">All relations</h2>
            <RelationsList relations={relations} nameById={nameById} />
          </div>
        </div>
      )}
    </section>
  )
}
