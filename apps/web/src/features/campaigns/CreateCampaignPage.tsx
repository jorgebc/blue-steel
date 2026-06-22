import { useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useTranslation } from 'react-i18next'
import { Loader2 } from 'lucide-react'
import { useCreateCampaign } from '@/api/campaigns'
import { useUserSearch, USER_SEARCH_MIN_LENGTH } from '@/api/users'
import { ApiClientError } from '@/api/client'
import { useDebouncedValue } from '@/hooks/useDebouncedValue'
import { useAuthStore } from '@/store/authStore'
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
import { Label } from '@/components/ui/label'

type FormValues = { name: string; gmUserId: string }

/**
 * Admin-only campaign creation: name the campaign, pick its GM by email, and
 * create it. Non-admins are redirected to the campaign list (D-024, D-051).
 */
export function CreateCampaignPage() {
  const { t } = useTranslation()
  const isAdmin = useAuthStore((s) => s.currentUser?.isAdmin)
  const navigate = useNavigate()
  const { mutate: createCampaign, isPending } = useCreateCampaign()
  const [banner, setBanner] = useState<string | null>(null)
  const [gmQuery, setGmQuery] = useState('')
  const [selectedGmEmail, setSelectedGmEmail] = useState<string | null>(null)

  const form = useForm<FormValues>({
    resolver: zodResolver(
      z.object({
        name: z.string().min(1, t('campaigns.nameRequired')),
        gmUserId: z.string().min(1, t('campaigns.gmRequired')),
      })
    ),
    defaultValues: { name: '', gmUserId: '' },
  })

  const debouncedGmQuery = useDebouncedValue(gmQuery.trim())
  const { data: gmResults = [], isFetching: isSearching } = useUserSearch(debouncedGmQuery)
  const searchActive = debouncedGmQuery.length >= USER_SEARCH_MIN_LENGTH

  if (!isAdmin) return <Navigate to="/" replace />

  function selectGm(user: { id: string; email: string }) {
    form.setValue('gmUserId', user.id, { shouldValidate: true })
    setSelectedGmEmail(user.email)
    setGmQuery('')
  }

  function onSubmit(values: FormValues) {
    setBanner(null)
    createCampaign(values, {
      onSuccess(campaign) {
        navigate(`/campaigns/${campaign.id}`)
      },
      onError(err) {
        if (err instanceof ApiClientError) {
          let hasFieldError = false
          for (const e of err.errors) {
            if (e.field) {
              form.setError(e.field as keyof FormValues, { message: e.message })
              hasFieldError = true
            }
          }
          if (!hasFieldError) {
            setBanner(err.errors[0]?.message ?? t('campaigns.createError'))
          }
        } else {
          setBanner(t('common.unexpectedError'))
        }
      },
    })
  }

  return (
    <main className="mx-auto max-w-lg p-6">
      <h1 className="mb-6 text-2xl font-semibold">{t('campaigns.newCampaign')}</h1>
      {banner && (
        <div className="mb-4">
          <InlineBanner variant="error" message={banner} onDismiss={() => setBanner(null)} />
        </div>
      )}
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6" noValidate>
          <FormField
            control={form.control}
            name="name"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t('campaigns.nameLabel')}</FormLabel>
                <FormControl>
                  <Input placeholder={t('campaigns.namePlaceholder')} {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          <div className="space-y-2">
            <Label htmlFor="gm-search">{t('campaigns.gameMaster')}</Label>
            {selectedGmEmail ? (
              <div className="flex items-center justify-between rounded-lg border border-border p-3 text-sm">
                <span>{selectedGmEmail}</span>
                <button
                  type="button"
                  onClick={() => {
                    form.setValue('gmUserId', '', { shouldValidate: true })
                    setSelectedGmEmail(null)
                  }}
                  className="text-xs text-accent underline-offset-4 hover:underline"
                >
                  {t('campaigns.change')}
                </button>
              </div>
            ) : (
              <>
                <Input
                  id="gm-search"
                  type="text"
                  inputMode="email"
                  placeholder={t('campaigns.searchUsers')}
                  autoComplete="off"
                  value={gmQuery}
                  onChange={(e) => setGmQuery(e.target.value)}
                />
                {gmResults.length > 0 && (
                  <ul className="rounded-lg border border-border">
                    {gmResults.map((user) => (
                      <li key={user.id}>
                        <button
                          type="button"
                          onClick={() => selectGm(user)}
                          className="w-full px-3 py-2 text-left text-sm hover:bg-muted"
                        >
                          {user.email}
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
                {searchActive && !isSearching && gmResults.length === 0 && (
                  <p className="text-sm text-muted-foreground">
                    {t('campaigns.noUsersFound', { query: debouncedGmQuery })}
                  </p>
                )}
              </>
            )}
            {form.formState.errors.gmUserId && (
              <p className="text-sm text-destructive">{form.formState.errors.gmUserId.message}</p>
            )}
          </div>

          <Button type="submit" className="w-full" disabled={isPending} aria-disabled={isPending}>
            {isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden />}
            {t('campaigns.createCampaign')}
          </Button>
        </form>
      </Form>
    </main>
  )
}
