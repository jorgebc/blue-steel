import { useTranslation } from 'react-i18next'

interface Props {
  summary: string
}

/** Display-only AI session summary shown before the diff (D-005). Plain text — never HTML. */
export function NarrativeSummaryHeader({ summary }: Props) {
  const { t } = useTranslation()
  return (
    <header className="rounded-2xl border border-border bg-surface p-6 shadow-sm">
      <h2 className="mb-2 text-xl font-semibold text-foreground">{t('input.sessionSummary')}</h2>
      <p className="max-w-prose text-sm text-foreground">{summary}</p>
    </header>
  )
}
