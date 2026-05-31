import { useEffect, useMemo } from 'react'
import { useForm } from 'react-hook-form'
import { FocusedOverlay } from '@/components/domain/FocusedOverlay'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import type { ExistingDiffCard, NewDiffCard } from '@/types/session'
import { formatFieldValue } from './fieldFormat'

interface Props {
  card: ExistingDiffCard | NewDiffCard
  open: boolean
  onClose: () => void
  onSave: (editedFields: Record<string, unknown>) => void
}

function editableFields(card: ExistingDiffCard | NewDiffCard): Record<string, unknown> {
  return card.cardType === 'EXISTING' ? card.changedFields : card.fullProfile
}

/**
 * Edits the editable fields of a Delta/New card inside a {@link FocusedOverlay}
 * (no modal, D-082). Save emits the edited fields; Cancel/ESC/backdrop closes
 * without saving. Only the diff's editable fields are shown — not a full entity
 * form (D-006/D-007).
 */
export function EditCardOverlay({ card, open, onClose, onSave }: Props) {
  const fields = editableFields(card)
  const keys = useMemo(() => Object.keys(fields), [fields])

  const defaultValues = useMemo(
    () => Object.fromEntries(keys.map((k) => [k, formatFieldValue(fields[k])])),
    [keys, fields]
  )

  const { register, handleSubmit, reset } = useForm<Record<string, string>>({ defaultValues })

  // Re-seed the form when switching to a different card.
  useEffect(() => {
    reset(defaultValues)
  }, [card.cardId, defaultValues, reset])

  function onSubmit(values: Record<string, string>) {
    onSave(values)
  }

  return (
    <FocusedOverlay open={open} onClose={onClose} ariaLabel={`Edit ${card.name}`}>
      <div className="w-[28rem] max-w-[90vw] bg-white p-6">
        <h3 className="mb-4 text-base font-medium text-slate-900">Edit {card.name}</h3>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          {keys.map((key) => (
            <div key={key} className="space-y-1">
              <Label htmlFor={`edit-${key}`}>{key}</Label>
              <Input id={`edit-${key}`} {...register(key)} />
            </div>
          ))}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit">Save</Button>
          </div>
        </form>
      </div>
    </FocusedOverlay>
  )
}
