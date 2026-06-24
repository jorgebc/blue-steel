import { useEffect, useMemo } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { FocusedOverlay } from '@/components/domain/FocusedOverlay'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import type { ExistingDiffCard, NewDiffCard } from '@/types/session'
import { decodeFieldValue, encodeFieldValue, fieldControl } from './fieldFormat'

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
 * (no modal, D-082). Save emits the edited fields (preserving each field's
 * original type); Cancel/ESC/backdrop closes without saving. Only the diff's
 * editable fields are shown — not a full entity form (D-006/D-007).
 */
export function EditCardOverlay({ card, open, onClose, onSave }: Props) {
  const { t } = useTranslation()
  const fields = editableFields(card)
  const keys = useMemo(() => Object.keys(fields), [fields])

  const defaultValues = useMemo(
    () => Object.fromEntries(keys.map((k) => [k, encodeFieldValue(fields[k])])),
    [keys, fields]
  )

  const { register, handleSubmit, reset } = useForm<Record<string, string>>({ defaultValues })

  // Re-seed the form when switching to a different card.
  useEffect(() => {
    reset(defaultValues)
  }, [card.cardId, defaultValues, reset])

  function onSubmit(values: Record<string, string>) {
    const editedFields = Object.fromEntries(
      keys.map((k) => [k, decodeFieldValue(values[k], fields[k])])
    )
    onSave(editedFields)
  }

  return (
    <FocusedOverlay
      open={open}
      onClose={onClose}
      ariaLabel={t('input.editCard', { name: card.name })}
    >
      <div className="w-[28rem] max-w-[90vw] bg-surface p-6">
        <h3 className="mb-4 text-base font-medium text-foreground">
          {t('input.editCard', { name: card.name })}
        </h3>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          {keys.map((key) => {
            const control = fieldControl(key, fields[key])
            return (
              <div key={key} className="space-y-1">
                <Label htmlFor={`edit-${key}`}>{key}</Label>
                {control === 'textarea' ? (
                  <Textarea id={`edit-${key}`} rows={4} {...register(key)} />
                ) : (
                  <Input id={`edit-${key}`} {...register(key)} />
                )}
                {control === 'array' && (
                  <p className="text-xs text-muted-foreground">{t('input.separateValues')}</p>
                )}
              </div>
            )
          })}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={onClose}>
              {t('common.cancel')}
            </Button>
            <Button type="submit">{t('common.save')}</Button>
          </div>
        </form>
      </div>
    </FocusedOverlay>
  )
}
