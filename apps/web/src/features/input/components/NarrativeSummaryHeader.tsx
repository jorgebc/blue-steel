interface Props {
  summary: string
}

/** Display-only AI session summary shown before the diff (D-005). Plain text — never HTML. */
export function NarrativeSummaryHeader({ summary }: Props) {
  return (
    <header className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
      <h2 className="mb-2 text-xl font-semibold text-slate-900">Session summary</h2>
      <p className="max-w-prose text-sm text-slate-700">{summary}</p>
    </header>
  )
}
