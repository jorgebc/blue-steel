import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
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

type FormValues = { sessionId: string }

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
  const { t } = useTranslation()
  const campaignId = useCampaignStore((s) => s.activeCampaignId)
  const { data: sessions } = useSessions(campaignId ?? '', 0)
  const { mutate, isPending } = useCreateProposal(campaignId ?? '')

  const [values, setValues] = useState<Record<string, string>>(() => editableSeed(currentSnapshot))
  const [deltaError, setDeltaError] = useState<string | null>(null)
  const [banner, setBanner] = useState<string | null>(null)

  const schema = useMemo(
    () =>
      z.object({
        sessionId: z.string().min(1, t('proposals.submitForm.sessionRequired')),
      }),
    [t]
  )

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { sessionId: '' },
  })

  function onSubmit(formValues: FormValues) {
    setBanner(null)
    setDeltaError(null)
    const proposedDelta = computeDelta(currentSnapshot, values)
    if (Object.keys(proposedDelta).length === 0) {
      setDeltaError(t('proposals.submitForm.emptyDelta'))
      return
    }
    mutate(
      { targetType, targetId, sessionId: formValues.sessionId, proposedDelta },
      {
        onSuccess: () => onSubmitted(),
        onError: (err) => {
          if (err instanceof ApiClientError) {
            setBanner(err.errors[0]?.message ?? t('proposals.submitForm.submitError'))
          } else {
            setBanner(t('common.unexpectedError'))
          }
        },
      }
    )
  }

  return (
    <div className="max-h-[80vh] w-[32rem] max-w-[90vw] overflow-y-auto bg-surface p-6">
      <h3 className="mb-1 text-base font-medium text-foreground">
        {t('proposals.submitForm.title')}
      </h3>
      <p className="mb-4 text-sm text-muted-foreground">
        {targetType === 'ACTOR'
          ? t('proposals.submitForm.descriptionActor')
          : t('proposals.submitForm.descriptionSpace')}
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
                <FormLabel>{t('proposals.submitForm.relatedSession')}</FormLabel>
                <Select onValueChange={field.onChange} value={field.value}>
                  <FormControl>
                    <SelectTrigger>
                      <SelectValue placeholder={t('proposals.submitForm.sessionPlaceholder')} />
                    </SelectTrigger>
                  </FormControl>
                  <SelectContent>
                    {(sessions ?? []).map((s) => (
                      <SelectItem key={s.sessionId} value={s.sessionId}>
                        {t('proposals.submitForm.sessionOption', {
                          sequence: s.sequenceNumber,
                          status: t(`sessions.status.${s.status.toLowerCase()}`).toLowerCase(),
                        })}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <FormMessage />
              </FormItem>
            )}
          />

          <div>
            <p className="mb-2 text-sm font-medium text-foreground">
              {t('proposals.submitForm.proposedFields')}
            </p>
            <DeltaFieldsEditor
              baseline={currentSnapshot}
              values={values}
              onChange={(key, value) => setValues((prev) => ({ ...prev, [key]: value }))}
              idPrefix="propose"
            />
            {deltaError && <p className="mt-2 text-sm text-error">{deltaError}</p>}
          </div>

          <div className="flex justify-end gap-3">
            <Button type="button" variant="outline" onClick={onCancel} disabled={isPending}>
              {t('common.cancel')}
            </Button>
            <Button type="submit" disabled={isPending} aria-disabled={isPending}>
              {isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden />}
              {t('proposals.submitForm.submit')}
            </Button>
          </div>
        </form>
      </Form>
    </div>
  )
}
