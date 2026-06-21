import { useId, useState, type ReactNode } from 'react'
import { ChevronDown, ChevronRight } from 'lucide-react'

interface Props {
  title: string
  count: number
  children: ReactNode
}

/** Collapsible per-category wrapper (Actors/Spaces/Events/Relations). Expand state is local — presentational, not global UI state. */
export function DiffCategorySection({ title, count, children }: Props) {
  const [expanded, setExpanded] = useState(true)
  const headingId = useId()
  const Chevron = expanded ? ChevronDown : ChevronRight

  return (
    <section aria-labelledby={headingId} className="space-y-3">
      <h2 id={headingId}>
        <button
          type="button"
          aria-expanded={expanded}
          onClick={() => setExpanded((e) => !e)}
          className="flex w-full items-center gap-2 text-left text-base font-medium text-foreground"
        >
          <Chevron className="h-4 w-4 text-muted-foreground" aria-hidden />
          {title} <span className="font-normal text-muted-foreground">({count})</span>
        </button>
      </h2>
      {expanded && <div className="space-y-3">{children}</div>}
    </section>
  )
}
