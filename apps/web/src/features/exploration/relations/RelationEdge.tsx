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

  return (
    <>
      <BaseEdge path={edgePath} />
      {data?.label && (
        <EdgeLabelRenderer>
          <span
            style={{
              transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)`,
            }}
            className="pointer-events-none absolute rounded-full bg-white px-2 py-0.5 text-xs text-slate-600 shadow-sm"
          >
            {data.label}
          </span>
        </EdgeLabelRenderer>
      )}
    </>
  )
}
