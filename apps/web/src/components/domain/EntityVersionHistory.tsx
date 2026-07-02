import { useTranslation } from 'react-i18next'
import type { TFunction } from 'i18next'
import type { EntityVersion } from '@/types/worldstate'

interface Props {
  versions: EntityVersion[]
}

function changedSummary(changedFields: Record<string, unknown>, t: TFunction): string {
  const keys = Object.keys(changedFields)
  if (keys.length === 0) return t('versionHistory.initial')
  return t('versionHistory.changed', { fields: keys.join(', ') })
}

function sessionRef(version: EntityVersion, t: TFunction): string {
  return version.sessionSequenceNumber !== null
    ? t('versionHistory.sessionRef', { sequence: version.sessionSequenceNumber })
    : t('versionHistory.uncommitted')
}

/**
 * Collapsible version history for a world-state entity (D-001, D-003). Each row is a native
 * `<details>` disclosure: the summary shows the version number, originating session, and a
 * one-line `changedFields` summary; expanding reveals the changed field values. Newest first.
 */
export function EntityVersionHistory({ versions }: Props) {
  const { t } = useTranslation()

  if (versions.length === 0) {
    return <p className="text-sm text-muted-foreground">{t('versionHistory.empty')}</p>
  }

  const ordered = [...versions].sort((a, b) => b.versionNumber - a.versionNumber)

  return (
    <ul className="space-y-2">
      {ordered.map((version) => (
        <li key={version.versionId}>
          <details className="rounded-xl border border-border bg-surface shadow-sm">
            <summary className="flex cursor-pointer items-center justify-between gap-4 px-4 py-3 text-sm">
              <span className="flex items-center gap-3">
                <span className="font-medium text-foreground">v{version.versionNumber}</span>
                <span className="text-muted-foreground">{sessionRef(version, t)}</span>
              </span>
              <span className="text-xs text-muted-foreground">
                {changedSummary(version.changedFields, t)}
              </span>
            </summary>
            <dl className="space-y-1 border-t border-border px-4 py-3 text-sm">
              {Object.keys(version.changedFields).length === 0 ? (
                <p className="text-muted-foreground">{t('versionHistory.createdAtVersion')}</p>
              ) : (
                Object.entries(version.changedFields).map(([key, value]) => (
                  <div key={key} className="flex gap-3">
                    <dt className="w-32 shrink-0 font-medium text-muted-foreground">{key}</dt>
                    <dd className="text-foreground">{String(value)}</dd>
                  </div>
                ))
              )}
            </dl>
          </details>
        </li>
      ))}
    </ul>
  )
}
