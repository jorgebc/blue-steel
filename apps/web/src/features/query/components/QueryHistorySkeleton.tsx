/**
 * Loading placeholder for the Query Mode history panel, shown while a page of past Q&As is in
 * flight. Mirrors the shape of {@link QueryHistoryPanel}'s list — a few question rows, each a
 * question line plus a smaller timestamp line — so there is zero layout shift when the real list
 * arrives (D-086, no spinner).
 */
export function QueryHistorySkeleton() {
  return (
    <div role="status" aria-label="Loading question history" className="space-y-3">
      {[0, 1, 2].map((i) => (
        <div key={i} className="space-y-2 rounded-lg border border-slate-200 p-4">
          <div className="h-4 w-3/4 animate-pulse rounded bg-slate-200" />
          <div className="h-3 w-24 animate-pulse rounded bg-slate-200" />
        </div>
      ))}
    </div>
  )
}
