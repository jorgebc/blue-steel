import { Handle, Position, type Node, type NodeProps } from '@xyflow/react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'
import { MapPin, User } from 'lucide-react'
import type { NodeData } from '@/types/relation'

/**
 * A read-only graph node for an actor or space. Clicking it navigates to that entity's profile
 * (D-010 — the graph never edits world state). The icon chip and type caption distinguish actors
 * from spaces using slate tones only; the blue ring (the sole permitted accent) appears when the
 * node is an endpoint of the selected relation ({@link NodeData.highlighted}). Handles are present so
 * relations can connect on either side but drag-to-connect is disabled at the container and the
 * handles are visually hidden via global CSS.
 */
export function RelationNode({ data }: NodeProps<Node<NodeData>>) {
  const { t } = useTranslation()
  const { campaignId } = useParams<{ campaignId: string }>()
  const isSpace = data.entityType === 'space'
  const segment = isSpace ? 'spaces' : 'entities'
  const Icon = isSpace ? MapPin : User
  const typeLabel = isSpace
    ? t('exploration.relations.nodeSpace')
    : t('exploration.relations.nodeActor')

  return (
    <>
      <Handle type="target" position={Position.Left} />
      <Link
        to={`/campaigns/${campaignId}/explore/${segment}/${data.entityId}`}
        title={data.name}
        // Disable the browser's native link-drag so React Flow's node drag takes over.
        draggable={false}
        className={[
          'flex w-44 items-center gap-2 rounded-xl border bg-surface px-3 py-2 shadow-sm transition-shadow duration-200 hover:shadow-md',
          data.highlighted
            ? 'border-accent ring-2 ring-ring/50'
            : 'border-border hover:border-border-strong',
        ].join(' ')}
      >
        <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-muted">
          <Icon className="h-4 w-4 text-muted-foreground" aria-hidden />
        </span>
        <span className="flex min-w-0 flex-col">
          <span className="truncate text-sm font-medium text-foreground">{data.name}</span>
          <span className="text-xs text-muted-foreground">{typeLabel}</span>
        </span>
      </Link>
      <Handle type="source" position={Position.Right} />
    </>
  )
}
