/**
 * Loading placeholder for an entity profile, shaped to the header + current-state block + version
 * history rows so the real profile drops in without layout shift (D-086).
 */
export function EntityProfileSkeleton() {
  return (
    <div role="status" aria-label="Loading profile" className="space-y-6">
      {/* header: name + type */}
      <div className="space-y-2">
        <div className="h-7 w-56 rounded bg-slate-200 animate-pulse" />
        <div className="h-4 w-24 rounded bg-slate-200 animate-pulse" />
      </div>

      {/* current state card */}
      <div className="space-y-3 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="h-4 w-32 rounded bg-slate-200 animate-pulse" />
        {Array.from({ length: 3 }).map((_, i) => (
          <div key={i} className="flex gap-4">
            <div className="h-3 w-24 rounded bg-slate-200 animate-pulse" />
            <div className="h-3 w-40 rounded bg-slate-200 animate-pulse" />
          </div>
        ))}
      </div>

      {/* version history */}
      <div className="space-y-2">
        <div className="h-4 w-36 rounded bg-slate-200 animate-pulse" />
        {Array.from({ length: 2 }).map((_, i) => (
          <div
            key={i}
            className="h-12 rounded-xl border border-slate-200 bg-white shadow-sm animate-pulse"
          />
        ))}
      </div>
    </div>
  )
}
