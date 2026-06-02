import type { EntityVersion } from '@/types/worldstate'

interface Props {
  versions: EntityVersion[]
}

function changedSummary(changedFields: Record<string, unknown>): string {
  const keys = Object.keys(changedFields)
  if (keys.length === 0) return 'Initial version'
  return `Changed: ${keys.join(', ')}`
}

function sessionRef(version: EntityVersion): string {
  return version.sessionSequenceNumber !== null
    ? `Session #${version.sessionSequenceNumber}`
    : 'Uncommitted session'
}

/**
 * Collapsible version history for a world-state entity (D-001, D-003). Each row is a native
 * `<details>` disclosure: the summary shows the version number, originating session, and a
 * one-line `changedFields` summary; expanding reveals the changed field values. Newest first.
 */
export function EntityVersionHistory({ versions }: Props) {
  if (versions.length === 0) {
    return <p className="text-sm text-slate-500">No version history yet.</p>
  }

  const ordered = [...versions].sort((a, b) => b.versionNumber - a.versionNumber)

  return (
    <ul className="space-y-2">
      {ordered.map((version) => (
        <li key={version.versionId}>
          <details className="rounded-xl border border-slate-200 bg-white shadow-sm">
            <summary className="flex cursor-pointer items-center justify-between gap-4 px-4 py-3 text-sm">
              <span className="flex items-center gap-3">
                <span className="font-medium text-slate-900">v{version.versionNumber}</span>
                <span className="text-slate-500">{sessionRef(version)}</span>
              </span>
              <span className="text-xs text-slate-400">
                {changedSummary(version.changedFields)}
              </span>
            </summary>
            <dl className="space-y-1 border-t border-slate-100 px-4 py-3 text-sm">
              {Object.keys(version.changedFields).length === 0 ? (
                <p className="text-slate-500">Entity created at this version.</p>
              ) : (
                Object.entries(version.changedFields).map(([key, value]) => (
                  <div key={key} className="flex gap-3">
                    <dt className="w-32 shrink-0 font-medium text-slate-500">{key}</dt>
                    <dd className="text-slate-900">{String(value)}</dd>
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
