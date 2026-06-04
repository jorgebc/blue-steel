/**
 * Loading placeholder for the relations graph (D-086 — no spinners in primary content). Fills the
 * graph canvas area with a few pulsing node-sized blocks so swapping in the real React Flow surface
 * causes no jarring shift.
 */
export function RelationsGraphSkeleton() {
  return (
    <div
      role="status"
      aria-label="Loading relations"
      className="relative h-[480px] w-full overflow-hidden rounded-2xl border border-slate-200 bg-white"
    >
      <div className="absolute left-8 top-10 h-9 w-32 rounded-lg bg-slate-200 animate-pulse" />
      <div className="absolute left-64 top-24 h-9 w-32 rounded-lg bg-slate-200 animate-pulse" />
      <div className="absolute left-40 top-56 h-9 w-32 rounded-lg bg-slate-200 animate-pulse" />
      <div className="absolute left-80 top-72 h-9 w-32 rounded-lg bg-slate-200 animate-pulse" />
    </div>
  )
}
