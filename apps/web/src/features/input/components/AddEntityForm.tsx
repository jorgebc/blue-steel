import { useState } from 'react'
import { useFieldArray, useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
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

const schema = z.object({
  entityType: z.enum(['actor', 'space']),
  name: z.string().trim().min(1, 'Name is required'),
  fields: z.array(z.object({ key: z.string(), value: z.string() })),
})

type FormValues = z.infer<typeof schema>

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
  const [banner, setBanner] = useState<string | null>(null)

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
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
        setBanner(`Duplicate field "${trimmedKey}". Field names must be unique.`)
        return
      }
      entityFields[trimmedKey] = value
    }
    onAdd({ entityType: values.entityType, name: values.name.trim(), fields: entityFields })
  }

  return (
    <div className="max-h-[80vh] w-[32rem] max-w-[90vw] overflow-y-auto bg-surface p-6">
      <h3 className="mb-1 text-base font-medium text-foreground">Add an entity</h3>
      <p className="mb-4 text-sm text-muted-foreground">
        Add an entity the extraction missed. It will be created when you commit this review.
      </p>

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
                <FormLabel>Type</FormLabel>
                <Select onValueChange={field.onChange} value={field.value}>
                  <FormControl>
                    <SelectTrigger>
                      <SelectValue placeholder="Select an entity type" />
                    </SelectTrigger>
                  </FormControl>
                  <SelectContent>
                    {ENTITY_TYPES.map((type) => (
                      <SelectItem key={type} value={type}>
                        {type.charAt(0).toUpperCase() + type.slice(1)}
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
                <FormLabel>Name</FormLabel>
                <FormControl>
                  <Input placeholder="e.g. Madam Eva" {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          <div>
            <p className="mb-2 text-sm font-medium text-foreground">Fields</p>
            <div className="space-y-2">
              {fields.length === 0 ? (
                <p className="text-sm text-muted-foreground">No fields yet.</p>
              ) : (
                fields.map((row, index) => (
                  <div key={row.id} className="flex items-center gap-2">
                    <Input
                      aria-label={`Field ${index + 1} name`}
                      placeholder="Field"
                      {...form.register(`fields.${index}.key`)}
                    />
                    <Input
                      aria-label={`Field ${index + 1} value`}
                      placeholder="Value"
                      {...form.register(`fields.${index}.value`)}
                    />
                    <Button
                      type="button"
                      variant="outline"
                      size="icon"
                      aria-label={`Remove field ${index + 1}`}
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
              Add field
            </Button>
          </div>

          <div className="flex justify-end gap-3">
            <Button type="button" variant="outline" onClick={onCancel}>
              Cancel
            </Button>
            <Button type="submit">Add entity</Button>
          </div>
        </form>
      </Form>
    </div>
  )
}
