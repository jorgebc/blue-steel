import { useState } from 'react'
import { Link, Navigate, useParams } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useTranslation } from 'react-i18next'
import { Loader2 } from 'lucide-react'
import { extractExistingSessionId, useSubmitSession } from '@/api/sessions'
import { ApiClientError } from '@/api/client'
import { useCampaignStore } from '@/store/campaignStore'
import { InlineBanner } from '@/components/domain/InlineBanner'
import { ProcessingStatusView } from './ProcessingStatusView'
import { Button } from '@/components/ui/button'
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { Textarea } from '@/components/ui/textarea'

type FormValues = { summaryText: string }

/**
 * Submission form for a raw session summary. Editors and GMs only — a `player`
 * is redirected to campaign home. On a successful submit the form is replaced by
 * the {@link ProcessingStatusView} for the accepted session.
 */
export function SubmitSessionPage() {
  const { t } = useTranslation()
  const { campaignId } = useParams<{ campaignId: string }>()
  const activeRole = useCampaignStore((s) => s.activeRole)
  const { mutate: submit, isPending } = useSubmitSession(campaignId ?? '')
  const [sessionId, setSessionId] = useState<string | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  // Draft-recovery target (D-054). Kept separate from the transient error banner
  // because the resume affordance must persist — a `warning` InlineBanner would
  // auto-clear after 8s (UX_CONSTITUTION §5) and take the recovery link with it.
  const [recoverySessionId, setRecoverySessionId] = useState<string | null>(null)

  const form = useForm<FormValues>({
    resolver: zodResolver(z.object({ summaryText: z.string().min(1, t('input.summaryRequired')) })),
    defaultValues: { summaryText: '' },
  })

  if (activeRole === 'player') {
    return <Navigate to={`/campaigns/${campaignId}`} replace />
  }

  function onSubmit(values: FormValues) {
    setErrorMessage(null)
    setRecoverySessionId(null)
    submit(values, {
      onSuccess(data) {
        setSessionId(data.sessionId)
      },
      onError(err) {
        if (!(err instanceof ApiClientError)) {
          setErrorMessage(t('common.unexpectedError'))
          return
        }
        const validation = err.errors.find((e) => e.code === 'VALIDATION_ERROR')
        if (validation) {
          form.setError('summaryText', { message: validation.message })
          return
        }
        const tooLarge = err.errors.find((e) => e.code === 'SUMMARY_TOO_LARGE')
        if (tooLarge) {
          setErrorMessage(tooLarge.message)
          return
        }
        const existingSessionId = extractExistingSessionId(err)
        if (existingSessionId) {
          setRecoverySessionId(existingSessionId)
          return
        }
        setErrorMessage(err.errors[0]?.message ?? t('input.submissionFailed'))
      },
    })
  }

  if (sessionId && campaignId) {
    return (
      <div className="mx-auto max-w-2xl p-8">
        <ProcessingStatusView campaignId={campaignId} sessionId={sessionId} />
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-2xl p-8">
      <div className="rounded-2xl bg-surface p-6 shadow-sm">
        <h1 className="mb-6 text-2xl font-semibold text-foreground">{t('input.newSession')}</h1>
        {recoverySessionId && (
          <div
            role="alert"
            className="mb-4 rounded-lg border border-warning/30 bg-warning-subtle p-4 text-sm text-warning-foreground"
          >
            <p>{t('input.recoveryNotice')}</p>
            <Link
              to={`/campaigns/${campaignId}/sessions/${recoverySessionId}/diff`}
              className="mt-2 inline-block font-medium underline underline-offset-4"
            >
              {t('input.resumeReview')}
            </Link>
          </div>
        )}
        {errorMessage && (
          <div className="mb-4">
            <InlineBanner
              variant="error"
              message={errorMessage}
              onDismiss={() => setErrorMessage(null)}
            />
          </div>
        )}
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4" noValidate>
            <FormField
              control={form.control}
              name="summaryText"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t('input.sessionSummary')}</FormLabel>
                  <FormControl>
                    <Textarea rows={12} placeholder={t('input.summaryPlaceholder')} {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <Button type="submit" disabled={isPending} aria-disabled={isPending}>
              {isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden />}
              {t('input.submitSession')}
            </Button>
          </form>
        </Form>
      </div>
    </div>
  )
}
