import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useEntityList } from '@/api/worldstate'
import { InlineBanner } from '@/components/domain/InlineBanner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { EntityListSkeleton } from './EntityListSkeleton'
import type { EntityType } from '@/types/worldstate'

/** Maps an entity type to its exploration route segment. */
const SEGMENT: Record<EntityType, string> = {
  actor: 'entities',
  space: 'spaces',
  event: 'events',
  relation: 'relations',
}

interface Props {
  entityType: EntityType
  title: string
  description: string
}

/**
 * Offset-paginated list of one world-state entity type, with rows linking to each entity's profile
 * (D-055). Shared by the Entities (actor) and Spaces views.
 */
export function EntityListView({ entityType, title, description }: Props) {
  const { campaignId } = useParams<{ campaignId: string }>()
  const navigate = useNavigate()
  const [page, setPage] = useState(0)
  const { data, isLoading, isError } = useEntityList(entityType, page)

  const segment = SEGMENT[entityType]
  const totalPages = data ? Math.max(1, Math.ceil(data.totalCount / data.size)) : 1
  const hasPrev = page > 0
  const hasNext = data ? (page + 1) * data.size < data.totalCount : false

  return (
    <section>
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-foreground">{title}</h1>
        <p className="text-sm text-muted-foreground">{description}</p>
      </div>

      {isError && (
        <div className="mb-4">
          <InlineBanner
            variant="error"
            message="Could not load this list. Please refresh the page."
            onDismiss={() => navigate(0)}
          />
        </div>
      )}

      {isLoading && <EntityListSkeleton />}

      {!isLoading && !isError && data && data.items.length === 0 && (
        <p className="text-sm text-muted-foreground">Nothing here yet.</p>
      )}

      {!isLoading && !isError && data && data.items.length > 0 && (
        <>
          <ul className="space-y-3">
            {data.items.map((entity) => (
              <li key={entity.entityId}>
                <Link
                  to={`/campaigns/${campaignId}/explore/${segment}/${entity.entityId}`}
                  className="flex items-center gap-4 rounded-2xl border border-border bg-surface p-4 shadow-sm transition-shadow duration-200 hover:shadow-md"
                >
                  <span className="flex-1 truncate font-medium text-foreground">{entity.name}</span>
                  <Badge variant="outline" className="bg-muted text-muted-foreground shrink-0">
                    v{entity.latestVersionNumber}
                  </Badge>
                </Link>
              </li>
            ))}
          </ul>

          <div className="mt-6 flex items-center justify-between">
            <Button
              type="button"
              variant="outline"
              disabled={!hasPrev}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              Previous
            </Button>
            <span className="text-sm text-muted-foreground">
              Page {page + 1} of {totalPages}
            </span>
            <Button
              type="button"
              variant="outline"
              disabled={!hasNext}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </Button>
          </div>
        </>
      )}
    </section>
  )
}
