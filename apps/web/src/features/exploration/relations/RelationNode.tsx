import { Handle, Position, type Node, type NodeProps } from '@xyflow/react'
import { Link, useParams } from 'react-router-dom'
import { MapPin, User } from 'lucide-react'
import type { NodeData } from '@/types/relation'

/**
 * A read-only graph node for an actor or space. Clicking it navigates to that entity's profile
 * (D-010 — the graph never edits world state). Handles are present so relations can connect on
 * either side but drag-to-connect is disabled at the container.
 */
export function RelationNode({ data }: NodeProps<Node<NodeData>>) {
  const { campaignId } = useParams<{ campaignId: string }>()
  const segment = data.entityType === 'space' ? 'spaces' : 'entities'
  const Icon = data.entityType === 'space' ? MapPin : User

  return (
    <>
      <Handle type="target" position={Position.Left} />
      <Link
        to={`/campaigns/${campaignId}/explore/${segment}/${data.entityId}`}
        className="flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm font-medium text-slate-900 shadow-sm transition-shadow duration-200 hover:shadow-md"
      >
        <Icon className="h-4 w-4 shrink-0 text-slate-400" aria-hidden />
        <span className="truncate">{data.name}</span>
      </Link>
      <Handle type="source" position={Position.Right} />
    </>
  )
}
