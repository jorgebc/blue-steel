import { useState } from 'react'
import { Link, Navigate, useParams } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
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

const schema = z.object({
  summaryText: z.string().min(1, 'A session summary is required'),
})

type FormValues = z.infer<typeof schema>

interface Banner {
  variant: 'error' | 'warning'
  message: string
  resumeHref?: string
}

/**
 * Submission form for a raw session summary. Editors and GMs only — a `player`
 * is redirected to campaign home. On a successful submit the form is replaced by
 * the {@link ProcessingStatusView} for the accepted session.
 */
export function SubmitSessionPage() {
  const { campaignId } = useParams<{ campaignId: string }>()
  const activeRole = useCampaignStore((s) => s.activeRole)
  const { mutate: submit, isPending } = useSubmitSession(campaignId ?? '')
  const [sessionId, setSessionId] = useState<string | null>(null)
  const [banner, setBanner] = useState<Banner | null>(null)

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { summaryText: '' },
  })

  if (activeRole === 'player') {
    return <Navigate to={`/campaigns/${campaignId}`} replace />
  }

  function onSubmit(values: FormValues) {
    setBanner(null)
    submit(values, {
      onSuccess(data) {
        setSessionId(data.sessionId)
      },
      onError(err) {
        if (!(err instanceof ApiClientError)) {
          setBanner({
            variant: 'error',
            message: 'An unexpected error occurred. Please try again.',
          })
          return
        }
        const validation = err.errors.find((e) => e.code === 'VALIDATION_ERROR')
        if (validation) {
          form.setError('summaryText', { message: validation.message })
          return
        }
        const tooLarge = err.errors.find((e) => e.code === 'SUMMARY_TOO_LARGE')
        if (tooLarge) {
          setBanner({ variant: 'error', message: tooLarge.message })
          return
        }
        const existingSessionId = extractExistingSessionId(err)
        if (existingSessionId) {
          setBanner({
            variant: 'warning',
            message: 'You already have an unfinished session review for this campaign.',
            resumeHref: `/campaigns/${campaignId}/sessions/${existingSessionId}/diff`,
          })
          return
        }
        setBanner({ variant: 'error', message: err.errors[0]?.message ?? 'Submission failed.' })
      },
    })
  }

  if (sessionId && campaignId) {
    return (
      <div className="mx-auto max-w-2xl p-6">
        <ProcessingStatusView campaignId={campaignId} sessionId={sessionId} />
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-2xl p-6">
      <div className="rounded-2xl bg-white p-8 shadow-sm">
        <h1 className="mb-6 text-2xl font-semibold text-slate-900">New session</h1>
        {banner && (
          <div className="mb-4">
            <InlineBanner
              variant={banner.variant}
              message={banner.message}
              onDismiss={() => setBanner(null)}
            />
            {banner.resumeHref && (
              <Link
                to={banner.resumeHref}
                className="mt-2 inline-block text-sm font-medium text-blue-500 underline-offset-4 hover:underline"
              >
                Resume your unfinished review
              </Link>
            )}
          </div>
        )}
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4" noValidate>
            <FormField
              control={form.control}
              name="summaryText"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Session summary</FormLabel>
                  <FormControl>
                    <Textarea
                      rows={12}
                      placeholder="Paste your raw session notes here…"
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <Button type="submit" disabled={isPending} aria-disabled={isPending}>
              {isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden />}
              Submit session
            </Button>
          </form>
        </Form>
      </div>
    </div>
  )
}
