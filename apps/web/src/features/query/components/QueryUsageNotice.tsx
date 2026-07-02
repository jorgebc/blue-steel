import { useTranslation } from 'react-i18next'
import type { QueryUsage } from '@/types/query'

const LOW_REQUESTS_THRESHOLD = 3

interface Props {
  usage?: QueryUsage
}

/**
 * Persistent advisory that Query Mode runs on Google's shared free AI tier, with the live daily
 * budget consumed and a low-headroom hint drawn from the backend's own accounting (D-096). Static
 * by design — not an `InlineBanner` (those auto-clear after 8s); this must stay visible as a
 * standing reminder to use the service in moderation.
 */
export function QueryUsageNotice({ usage }: Props) {
  const { t } = useTranslation()
  const budgetUsed =
    usage && usage.capUsd > 0
      ? Math.min(100, Math.round((usage.consumedUsd / usage.capUsd) * 100))
      : null

  return (
    <div
      role="note"
      className="rounded-2xl border border-info/30 bg-info-subtle p-4 text-sm text-info-foreground"
    >
      <p>{t('query.usageNotice.body')}</p>
      {usage && budgetUsed !== null && (
        <p className="mt-1 text-info-foreground/80">
          {t('query.usageNotice.budgetUsed', { percent: budgetUsed })}
          {usage.requestsRemaining <= LOW_REQUESTS_THRESHOLD &&
            t('query.usageNotice.requestsRemaining', { count: usage.requestsRemaining })}
        </p>
      )}
    </div>
  )
}
