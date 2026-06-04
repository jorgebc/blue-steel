/**
 * Loading placeholder for the Timeline feed, shaped to the `EventCard` row (name + type badge over a
 * details line) so swapping in real events causes zero layout shift (D-086 — no spinners in primary
 * content).
 */
export function TimelineSkeleton() {
  return (
    <div role="status" aria-label="Loading timeline" className="space-y-3">
      {Array.from({ length: 5 }).map((_, i) => (
        <div
          key={i}
          className="flex items-center gap-4 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm"
        >
          <div className="flex-1 space-y-2">
            {/* event name */}
            <div className="h-4 w-48 rounded bg-slate-200 animate-pulse" />
            {/* actors / space / session line */}
            <div className="h-3 w-64 rounded bg-slate-200 animate-pulse" />
          </div>
          {/* event-type badge */}
          <div className="h-5 w-16 rounded-full bg-slate-200 animate-pulse shrink-0" />
        </div>
      ))}
    </div>
  )
}
