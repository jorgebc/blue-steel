import { useState } from 'react'
import { useFieldArray, useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useTranslation } from 'react-i18next'
import { Plus, Trash2 } from 'lucide-react'
import { InlineBanner } from '@/components/domain/InlineBanner'
import { Button } from '@/components/ui/button'
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import type { AddedEntityPayload, EntityType } from '@/types/session'

// Manual-add is limited to self-contained entities. Events and relations depend on structured
// links (endpoints, involved actors, event type) this generic form can't supply, so they are
// excluded here and rejected by the backend (F6.1, D-053).
const ENTITY_TYPES: EntityType[] = ['actor', 'space']

type FormValues = {
  entityType: 'actor' | 'space'
  name: string
  fields: { key: string; value: string }[]
}

interface Props {
  onAdd: (entity: AddedEntityPayload) => void
  onCancel: () => void
}

/**
 * Captures a new entity the extraction missed (F6.2, D-053): a type picker, a name, and a simple
 * key/value field editor. Blank-keyed rows are dropped; the assembled entity is handed to `onAdd`
 * for the diff-review commit payload — it is never submitted directly here.
 */
export function AddEntityForm({ onAdd, onCancel }: Props) {
  const { t } = useTranslation()
  const [banner, setBanner] = useState<string | null>(null)

  const form = useForm<FormValues>({
    resolver: zodResolver(
      z.object({
        entityType: z.enum(['actor', 'space']),
        name: z.string().trim().min(1, t('input.nameRequired')),
        fields: z.array(z.object({ key: z.string(), value: z.string() })),
      })
    ),
    defaultValues: { entityType: 'actor', name: '', fields: [] },
  })
  const { fields, append, remove } = useFieldArray({ control: form.control, name: 'fields' })

  function onSubmit(values: FormValues) {
    setBanner(null)
    const entityFields: Record<string, unknown> = {}
    for (const { key, value } of values.fields) {
      const trimmedKey = key.trim()
      if (trimmedKey.length === 0) continue
      // A duplicate key would silently overwrite an earlier row — surface it instead of guessing.
      if (trimmedKey in entityFields) {
        setBanner(t('input.duplicateField', { name: trimmedKey }))
        return
      }
      entityFields[trimmedKey] = value
    }
    onAdd({ entityType: values.entityType, name: values.name.trim(), fields: entityFields })
  }

  return (
    <div className="max-h-[80vh] w-[32rem] max-w-[90vw] overflow-y-auto bg-surface p-6">
      <h3 className="mb-1 text-base font-medium text-foreground">{t('input.addEntityTitle')}</h3>
      <p className="mb-4 text-sm text-muted-foreground">{t('input.addEntityDescription')}</p>

      {banner && (
        <div className="mb-4">
          <InlineBanner variant="error" message={banner} onDismiss={() => setBanner(null)} />
        </div>
      )}

      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4" noValidate>
          <FormField
            control={form.control}
            name="entityType"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t('input.typeLabel')}</FormLabel>
                <Select onValueChange={field.onChange} value={field.value}>
                  <FormControl>
                    <SelectTrigger>
                      <SelectValue placeholder={t('input.selectEntityType')} />
                    </SelectTrigger>
                  </FormControl>
                  <SelectContent>
                    {ENTITY_TYPES.map((type) => (
                      <SelectItem key={type} value={type}>
                        {t(`input.entityType_${type}`)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="name"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t('input.nameLabel')}</FormLabel>
                <FormControl>
                  <Input placeholder={t('input.namePlaceholder')} {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          <div>
            <p className="mb-2 text-sm font-medium text-foreground">{t('input.fields')}</p>
            <div className="space-y-2">
              {fields.length === 0 ? (
                <p className="text-sm text-muted-foreground">{t('input.noFieldsYet')}</p>
              ) : (
                fields.map((row, index) => (
                  <div key={row.id} className="flex items-center gap-2">
                    <Input
                      aria-label={t('input.fieldNameAria', { index: index + 1 })}
                      placeholder={t('input.fieldPlaceholder')}
                      {...form.register(`fields.${index}.key`)}
                    />
                    <Input
                      aria-label={t('input.fieldValueAria', { index: index + 1 })}
                      placeholder={t('input.valuePlaceholder')}
                      {...form.register(`fields.${index}.value`)}
                    />
                    <Button
                      type="button"
                      variant="outline"
                      size="icon"
                      aria-label={t('input.removeFieldAria', { index: index + 1 })}
                      onClick={() => remove(index)}
                    >
                      <Trash2 className="h-4 w-4" aria-hidden />
                    </Button>
                  </div>
                ))
              )}
            </div>
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="mt-2"
              onClick={() => append({ key: '', value: '' })}
            >
              <Plus className="mr-2 h-4 w-4" aria-hidden />
              {t('common.addField')}
            </Button>
          </div>

          <div className="flex justify-end gap-3">
            <Button type="button" variant="outline" onClick={onCancel}>
              {t('common.cancel')}
            </Button>
            <Button type="submit">{t('input.addEntity')}</Button>
          </div>
        </form>
      </Form>
    </div>
  )
}
