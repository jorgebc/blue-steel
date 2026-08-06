import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Search } from 'lucide-react'
import { useEntityList } from '@/api/worldstate'
import { useDebouncedValue } from '@/hooks/useDebouncedValue'
import { InlineBanner } from '@/components/domain/InlineBanner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
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
  const { t } = useTranslation()
  const { campaignId } = useParams<{ campaignId: string }>()
  const navigate = useNavigate()
  const [page, setPage] = useState(0)
  const [searchInput, setSearchInput] = useState('')
  // Debounce keystrokes into the applied search term (server-side ILIKE) instead of querying per
  // key; the query only refetches once typing pauses for 300ms.
  const search = useDebouncedValue(searchInput.trim(), 300)
  const { data, isLoading, isError } = useEntityList(entityType, page, search)

  // A new applied search term restarts paging from the first page. Adjusting state during
  // render (rather than in an effect) is React's recommended pattern for this.
  const [prevSearch, setPrevSearch] = useState(search)
  if (search !== prevSearch) {
    setPrevSearch(search)
    setPage(0)
  }

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

      <div className="relative mb-4 max-w-sm">
        <Search
          className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground"
          aria-hidden
        />
        <Input
          type="search"
          value={searchInput}
          onChange={(e) => setSearchInput(e.target.value)}
          placeholder={t('exploration.searchByName')}
          aria-label={t('exploration.searchByNameAria')}
          className="pl-9"
        />
      </div>

      {isError && (
        <div className="mb-4">
          <InlineBanner
            variant="error"
            message={t('exploration.listLoadError')}
            onDismiss={() => navigate(0)}
          />
        </div>
      )}

      {isLoading && <EntityListSkeleton />}

      {!isLoading && !isError && data && data.items.length === 0 && (
        <p className="text-sm text-muted-foreground">
          {search ? t('exploration.noMatch', { query: search }) : t('exploration.nothingHere')}
        </p>
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
              {t('exploration.previous')}
            </Button>
            <span className="text-sm text-muted-foreground">
              {t('exploration.pageOf', { page: page + 1, total: totalPages })}
            </span>
            <Button
              type="button"
              variant="outline"
              disabled={!hasNext}
              onClick={() => setPage((p) => p + 1)}
            >
              {t('exploration.next')}
            </Button>
          </div>
        </>
      )}
    </section>
  )
}
