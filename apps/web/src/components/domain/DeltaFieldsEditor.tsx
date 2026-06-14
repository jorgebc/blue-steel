import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { isEditablePrimitive } from '@/lib/proposalDelta'

interface Props {
  /** The baseline map whose fields are edited (an entity snapshot, or an author's proposed delta). */
  baseline: Record<string, unknown>
  /** Current edited string values, keyed by field. Only primitive fields appear here. */
  values: Record<string, string>
  onChange: (key: string, value: string) => void
  /** Stable prefix so multiple editors on one page keep unique input ids. */
  idPrefix: string
}

/**
 * Renders one editable text input per primitive field of {@link Props.baseline}; array/object fields
 * are shown read-only (editing structured data is out of scope for v2). Shared by the proposal
 * submission form and the GM approve-with-edit overlay.
 */
export function DeltaFieldsEditor({ baseline, values, onChange, idPrefix }: Props) {
  const entries = Object.entries(baseline)

  if (entries.length === 0) {
    return <p className="text-sm text-slate-500">This entity has no recorded fields to change.</p>
  }

  return (
    <div className="space-y-3">
      {entries.map(([key, value]) => {
        const id = `${idPrefix}-${key}`
        if (!isEditablePrimitive(value)) {
          return (
            <div key={key}>
              <Label className="text-slate-500">{key}</Label>
              <p className="mt-1 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-500">
                {JSON.stringify(value)} <span className="text-xs">(not editable)</span>
              </p>
            </div>
          )
        }
        return (
          <div key={key}>
            <Label htmlFor={id}>{key}</Label>
            <Input
              id={id}
              value={values[key] ?? ''}
              onChange={(e) => onChange(key, e.target.value)}
              className="mt-1"
            />
          </div>
        )
      })}
    </div>
  )
}
