import { useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
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

const schema = z.object({
  name: z.string().min(1, 'Campaign name is required'),
  gmUserId: z.string().min(1, 'Select a GM for this campaign'),
})

type FormValues = z.infer<typeof schema>

/**
 * Admin-only campaign creation: name the campaign, pick its GM by email, and
 * create it. Non-admins are redirected to the campaign list (D-024, D-051).
 */
export function CreateCampaignPage() {
  const isAdmin = useAuthStore((s) => s.currentUser?.isAdmin)
  const navigate = useNavigate()
  const { mutate: createCampaign, isPending } = useCreateCampaign()
  const [banner, setBanner] = useState<string | null>(null)
  const [gmQuery, setGmQuery] = useState('')
  const [selectedGmEmail, setSelectedGmEmail] = useState<string | null>(null)

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
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
            setBanner(err.errors[0]?.message ?? 'Could not create the campaign. Please try again.')
          }
        } else {
          setBanner('An unexpected error occurred. Please try again.')
        }
      },
    })
  }

  return (
    <main className="mx-auto max-w-lg p-6">
      <h1 className="mb-6 text-2xl font-semibold">New campaign</h1>
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
                <FormLabel>Campaign name</FormLabel>
                <FormControl>
                  <Input placeholder="Curse of Strahd" {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          <div className="space-y-2">
            <Label htmlFor="gm-search">Game master</Label>
            {selectedGmEmail ? (
              <div className="flex items-center justify-between rounded-lg border border-slate-200 p-3 text-sm">
                <span>{selectedGmEmail}</span>
                <button
                  type="button"
                  onClick={() => {
                    form.setValue('gmUserId', '', { shouldValidate: true })
                    setSelectedGmEmail(null)
                  }}
                  className="text-xs text-blue-500 underline-offset-4 hover:underline"
                >
                  Change
                </button>
              </div>
            ) : (
              <>
                <Input
                  id="gm-search"
                  type="text"
                  inputMode="email"
                  placeholder="Search users by email"
                  autoComplete="off"
                  value={gmQuery}
                  onChange={(e) => setGmQuery(e.target.value)}
                />
                {gmResults.length > 0 && (
                  <ul className="rounded-lg border border-slate-200">
                    {gmResults.map((user) => (
                      <li key={user.id}>
                        <button
                          type="button"
                          onClick={() => selectGm(user)}
                          className="w-full px-3 py-2 text-left text-sm hover:bg-slate-50"
                        >
                          {user.email}
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
                {searchActive && !isSearching && gmResults.length === 0 && (
                  <p className="text-sm text-slate-500">No users found for “{debouncedGmQuery}”.</p>
                )}
              </>
            )}
            {form.formState.errors.gmUserId && (
              <p className="text-sm text-destructive">{form.formState.errors.gmUserId.message}</p>
            )}
          </div>

          <Button type="submit" className="w-full" disabled={isPending} aria-disabled={isPending}>
            {isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden />}
            Create campaign
          </Button>
        </form>
      </Form>
    </main>
  )
}
