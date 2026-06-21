import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Loader2 } from 'lucide-react'
import { useCreateProposal } from '@/api/proposals'
import { useSessions } from '@/api/sessions'
import { ApiClientError } from '@/api/client'
import { useCampaignStore } from '@/store/campaignStore'
import { InlineBanner } from '@/components/domain/InlineBanner'
import { DeltaFieldsEditor } from '@/components/domain/DeltaFieldsEditor'
import { Button } from '@/components/ui/button'
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { computeDelta, editableSeed } from '@/lib/proposalDelta'
import type { ProposalTargetType } from '@/types/proposal'

const schema = z.object({
  sessionId: z.string().min(1, 'Select the session this change relates to'),
})

type FormValues = z.infer<typeof schema>

interface Props {
  targetType: ProposalTargetType
  targetId: string
  /** The entity's current snapshot (latest version's full snapshot); seeds the editable fields. */
  currentSnapshot: Record<string, unknown>
  onSubmitted: () => void
  onCancel: () => void
}

/**
 * Submission form for a "propose a change" overlay: a provenance-session picker (D-107) plus an
 * editable view of the entity's current fields. The submitted `proposedDelta` is the changed-field
 * subset (D-104); an empty delta is blocked client-side (the backend would return 422 EMPTY_DELTA).
 */
export function ProposalSubmitForm({
  targetType,
  targetId,
  currentSnapshot,
  onSubmitted,
  onCancel,
}: Props) {
  const campaignId = useCampaignStore((s) => s.activeCampaignId)
  const { data: sessions } = useSessions(campaignId ?? '', 0)
  const { mutate, isPending } = useCreateProposal(campaignId ?? '')

  const [values, setValues] = useState<Record<string, string>>(() => editableSeed(currentSnapshot))
  const [deltaError, setDeltaError] = useState<string | null>(null)
  const [banner, setBanner] = useState<string | null>(null)

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { sessionId: '' },
  })

  function onSubmit(formValues: FormValues) {
    setBanner(null)
    setDeltaError(null)
    const proposedDelta = computeDelta(currentSnapshot, values)
    if (Object.keys(proposedDelta).length === 0) {
      setDeltaError('Change at least one field before proposing.')
      return
    }
    mutate(
      { targetType, targetId, sessionId: formValues.sessionId, proposedDelta },
      {
        onSuccess: () => onSubmitted(),
        onError: (err) => {
          if (err instanceof ApiClientError) {
            setBanner(err.errors[0]?.message ?? 'Could not submit your proposal. Try again.')
          } else {
            setBanner('An unexpected error occurred. Please try again.')
          }
        },
      }
    )
  }

  return (
    <div className="max-h-[80vh] w-[32rem] max-w-[90vw] overflow-y-auto bg-surface p-6">
      <h3 className="mb-1 text-base font-medium text-foreground">Propose a change</h3>
      <p className="mb-4 text-sm text-muted-foreground">
        Suggest edits to this {targetType.toLowerCase()}. Other members can co-sign, then the GM
        decides.
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
            name="sessionId"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Related session</FormLabel>
                <Select onValueChange={field.onChange} value={field.value}>
                  <FormControl>
                    <SelectTrigger>
                      <SelectValue placeholder="Which session does this relate to?" />
                    </SelectTrigger>
                  </FormControl>
                  <SelectContent>
                    {(sessions ?? []).map((s) => (
                      <SelectItem key={s.sessionId} value={s.sessionId}>
                        Session #{s.sequenceNumber} ({s.status.toLowerCase()})
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <FormMessage />
              </FormItem>
            )}
          />

          <div>
            <p className="mb-2 text-sm font-medium text-foreground">Proposed fields</p>
            <DeltaFieldsEditor
              baseline={currentSnapshot}
              values={values}
              onChange={(key, value) => setValues((prev) => ({ ...prev, [key]: value }))}
              idPrefix="propose"
            />
            {deltaError && <p className="mt-2 text-sm text-red-600">{deltaError}</p>}
          </div>

          <div className="flex justify-end gap-3">
            <Button type="button" variant="outline" onClick={onCancel} disabled={isPending}>
              Cancel
            </Button>
            <Button type="submit" disabled={isPending} aria-disabled={isPending}>
              {isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden />}
              Submit proposal
            </Button>
          </div>
        </form>
      </Form>
    </div>
  )
}
