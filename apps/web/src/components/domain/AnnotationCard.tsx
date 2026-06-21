import { Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import type { Annotation } from '@/types/annotation'

interface Props {
  annotation: Annotation
  /** Whether the current user may delete this annotation (author or GM). */
  canDelete: boolean
  onDelete: () => void
}

/** Shortens a raw author UUID for display — there is no user-name lookup endpoint in v1. */
function authorLabel(authorId: string): string {
  return `Member ${authorId.slice(0, 8)}`
}

function formatTimestamp(iso: string): string {
  return new Date(iso).toLocaleString()
}

/**
 * A single non-canonical annotation (D-011). Immutable by design — there is no edit affordance and
 * no `updatedAt`. The delete button is rendered only when the current user is the author or a GM;
 * the backend enforces the same rule as defence in depth.
 */
export function AnnotationCard({ annotation, canDelete, onDelete }: Props) {
  return (
    <article className="rounded-xl border border-amber-200 bg-amber-50/60 p-4">
      <div className="flex items-start justify-between gap-3">
        <div className="text-xs text-muted-foreground">
          <span className="font-medium text-foreground">{authorLabel(annotation.authorId)}</span>
          <span aria-hidden> · </span>
          <time dateTime={annotation.createdAt}>{formatTimestamp(annotation.createdAt)}</time>
        </div>
        {canDelete && (
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            aria-label="Delete annotation"
            onClick={onDelete}
          >
            <Trash2 className="h-4 w-4 text-muted-foreground" />
          </Button>
        )}
      </div>
      <p className="mt-2 text-sm whitespace-pre-wrap text-foreground">{annotation.content}</p>
    </article>
  )
}
