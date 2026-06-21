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
  const budgetUsed =
    usage && usage.capUsd > 0
      ? Math.min(100, Math.round((usage.consumedUsd / usage.capUsd) * 100))
      : null

  return (
    <div
      role="note"
      className="rounded-2xl border border-blue-200 bg-blue-50 p-4 text-sm text-blue-800 dark:border-blue-900 dark:bg-blue-950 dark:text-blue-200"
    >
      <p>
        Query Mode runs on Google's free AI tier, with daily limits shared across everyone. Please
        use it in moderation.
      </p>
      {usage && budgetUsed !== null && (
        <p className="mt-1 text-blue-700 dark:text-blue-300">
          Shared daily budget: {budgetUsed}% used
          {usage.requestsRemaining <= LOW_REQUESTS_THRESHOLD &&
            ` · ${usage.requestsRemaining} questions left this minute`}
        </p>
      )}
    </div>
  )
}
