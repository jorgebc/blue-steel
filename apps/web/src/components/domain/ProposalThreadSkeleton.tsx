/**
 * Loading placeholder for {@link ProposalThread}. Dimensions are derived from a proposal row (status
 * badge, two delta lines, a co-sign button) to match the loaded layout within ±2px and avoid shift
 * (D-086 — no spinners in content).
 */
export function ProposalThreadSkeleton() {
  return (
    <div role="status" aria-label="Loading proposals" className="space-y-3">
      {[0, 1].map((i) => (
        <div key={i} className="rounded-xl border border-border bg-surface p-4 shadow-sm">
          <div className="mb-3 flex items-center justify-between">
            <div className="h-5 w-20 animate-pulse rounded-full bg-muted" />
            <div className="h-3 w-24 animate-pulse rounded bg-muted" />
          </div>
          <div className="space-y-2">
            <div className="h-4 w-3/4 animate-pulse rounded bg-muted" />
            <div className="h-4 w-1/2 animate-pulse rounded bg-muted" />
          </div>
          <div className="mt-4 h-9 w-24 animate-pulse rounded-lg bg-muted" />
        </div>
      ))}
    </div>
  )
}
