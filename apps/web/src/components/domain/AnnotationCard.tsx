import { useTranslation } from 'react-i18next'
import { Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import type { Annotation } from '@/types/annotation'

interface Props {
  annotation: Annotation
  /** Whether the current user may delete this annotation (author or GM). */
  canDelete: boolean
  onDelete: () => void
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
  const { t } = useTranslation()
  return (
    <article className="rounded-xl border border-amber-200 bg-amber-50/60 p-4 dark:border-amber-900 dark:bg-amber-950/30">
      <div className="flex items-start justify-between gap-3">
        <div className="text-xs text-muted-foreground">
          <span className="font-medium text-foreground">
            {t('annotations.authorLabel', { id: annotation.authorId.slice(0, 8) })}
          </span>
          <span aria-hidden> · </span>
          <time dateTime={annotation.createdAt}>{formatTimestamp(annotation.createdAt)}</time>
        </div>
        {canDelete && (
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            aria-label={t('annotations.deleteAria')}
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
