import {
  BaseEdge,
  EdgeLabelRenderer,
  getBezierPath,
  type Edge,
  type EdgeProps,
} from '@xyflow/react'
import type { EdgeData } from '@/types/relation'

/**
 * A labelled relation edge (F4.3.10). The label shows the relation kind (falling back to its name,
 * applied in {@link graphTransform}). Read-only — there is no edit affordance (D-010).
 */
export function RelationEdge({
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  markerEnd,
  data,
}: EdgeProps<Edge<EdgeData>>) {
  const [edgePath, labelX, labelY] = getBezierPath({
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
  })

  const selected = data?.selected ?? false

  return (
    <>
      {/* style is the React Flow API for dynamic SVG stroke; blue-500 emphasis is the one permitted accent. */}
      <BaseEdge
        path={edgePath}
        markerEnd={markerEnd}
        style={selected ? { stroke: '#3b82f6', strokeWidth: 2 } : undefined}
      />
      {data?.label && (
        <EdgeLabelRenderer>
          <span
            style={{
              transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)`,
            }}
            className={[
              'pointer-events-none absolute rounded-full border px-2 py-0.5 text-xs font-medium shadow-sm',
              selected
                ? 'border-blue-500 bg-blue-50 text-blue-700'
                : 'border-slate-200 bg-white text-slate-700',
            ].join(' ')}
          >
            {data.label}
          </span>
        </EdgeLabelRenderer>
      )}
    </>
  )
}
