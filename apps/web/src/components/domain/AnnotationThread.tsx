import { useState } from 'react'
import { useAnnotations, usePostAnnotation, useDeleteAnnotation } from '@/api/annotations'
import { useAuthStore } from '@/store/authStore'
import { useCampaignStore } from '@/store/campaignStore'
import { FocusedOverlay } from '@/components/domain/FocusedOverlay'
import { InlineBanner } from '@/components/domain/InlineBanner'
import { AnnotationCard } from '@/components/domain/AnnotationCard'
import { AnnotationInput } from '@/components/domain/AnnotationInput'
import { Button } from '@/components/ui/button'
import type { Annotation, AnnotationEntityType } from '@/types/annotation'

interface Props {
  entityType: AnnotationEntityType
  entityId: string
}

type Feedback = { variant: 'success' | 'error'; message: string } | null

function ThreadSkeleton() {
  return (
    <div role="status" aria-label="Loading annotations" className="space-y-3">
      {[0, 1].map((i) => (
        <div
          key={i}
          className="rounded-xl border border-amber-200 bg-amber-50/60 p-4 dark:border-amber-900 dark:bg-amber-950/30"
        >
          <div className="mb-2 h-3 w-32 animate-pulse rounded bg-amber-200/70 dark:bg-amber-900/70" />
          <div className="h-4 w-3/4 animate-pulse rounded bg-amber-200/70 dark:bg-amber-900/70" />
        </div>
      ))}
    </div>
  )
}

/**
 * The non-canonical annotation thread for one world-state entity (D-011): lists annotations, lets any
 * campaign member post, and lets the author or a GM delete (D-043). Visually separated from canonical
 * content. Delete uses a {@link FocusedOverlay} confirm (no modals); post/delete feedback via
 * {@link InlineBanner} (no toasts). Campaign/role/user context is read from the Zustand stores.
 */
export function AnnotationThread({ entityType, entityId }: Props) {
  const campaignId = useCampaignStore((s) => s.activeCampaignId)
  const activeRole = useCampaignStore((s) => s.activeRole)
  const currentUserId = useAuthStore((s) => s.currentUser?.id)

  const { data, isLoading, isError } = useAnnotations(campaignId ?? '', entityType, entityId)
  const post = usePostAnnotation(campaignId ?? '')
  const del = useDeleteAnnotation(campaignId ?? '')

  const [feedback, setFeedback] = useState<Feedback>(null)
  const [pendingDelete, setPendingDelete] = useState<Annotation | null>(null)

  function canDelete(annotation: Annotation): boolean {
    return currentUserId === annotation.authorId || activeRole === 'gm'
  }

  function handlePost(content: string) {
    setFeedback(null)
    post.mutate(
      { entityType, entityId, content },
      {
        onSuccess: () => setFeedback({ variant: 'success', message: 'Annotation posted.' }),
        onError: (err) => {
          console.error('Annotation post failed', err)
          setFeedback({ variant: 'error', message: "We couldn't post your annotation. Try again." })
        },
      }
    )
  }

  function handleDeleteConfirm() {
    if (!pendingDelete) return
    const target = pendingDelete
    setFeedback(null)
    del.mutate(
      { annotationId: target.id, entityType, entityId },
      {
        onSuccess: () => {
          setPendingDelete(null)
          setFeedback({ variant: 'success', message: 'Annotation deleted.' })
        },
        onError: (err) => {
          console.error('Annotation delete failed', err)
          setPendingDelete(null)
          setFeedback({
            variant: 'error',
            message: "We couldn't delete that annotation. Try again.",
          })
        },
      }
    )
  }

  const annotations = data ?? []

  return (
    <section
      aria-label="Annotations"
      className="mt-8 border-t-2 border-dashed border-amber-300 pt-6 dark:border-amber-800"
    >
      <header className="mb-4">
        <h2 className="text-sm font-semibold text-amber-900 dark:text-amber-200">Annotations</h2>
        <p className="text-xs text-amber-700 dark:text-amber-300">
          Player commentary — not canonical world state. Any member can post; the author or a GM can
          delete.
        </p>
      </header>

      {feedback && (
        <div className="mb-4">
          <InlineBanner
            variant={feedback.variant}
            message={feedback.message}
            onDismiss={() => setFeedback(null)}
          />
        </div>
      )}

      {isLoading && <ThreadSkeleton />}

      {isError && (
        <InlineBanner
          variant="error"
          message="Could not load annotations. Please refresh the page."
          onDismiss={() => undefined}
        />
      )}

      {!isLoading && !isError && (
        <div className="space-y-4">
          {annotations.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              No annotations yet. Be the first to add one.
            </p>
          ) : (
            <div className="space-y-3">
              {annotations.map((annotation) => (
                <AnnotationCard
                  key={annotation.id}
                  annotation={annotation}
                  canDelete={canDelete(annotation)}
                  onDelete={() => setPendingDelete(annotation)}
                />
              ))}
            </div>
          )}

          <AnnotationInput onSubmit={handlePost} isPending={post.isPending} />
        </div>
      )}

      <FocusedOverlay
        open={pendingDelete !== null}
        onClose={() => setPendingDelete(null)}
        ariaLabel="Delete annotation"
      >
        <div className="w-[24rem] max-w-[90vw] bg-surface p-6">
          <h3 className="mb-2 text-base font-medium text-foreground">Delete this annotation?</h3>
          <p className="mb-6 text-sm text-muted-foreground">
            This permanently removes the annotation. This cannot be undone.
          </p>
          <div className="flex justify-end gap-3">
            <Button
              type="button"
              variant="outline"
              onClick={() => setPendingDelete(null)}
              disabled={del.isPending}
            >
              Cancel
            </Button>
            <Button
              type="button"
              variant="destructive"
              onClick={handleDeleteConfirm}
              disabled={del.isPending}
            >
              Delete
            </Button>
          </div>
        </div>
      </FocusedOverlay>
    </section>
  )
}
