import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Loader2 } from 'lucide-react'
import { useUpdateProfile } from '@/api/users'
import { ApiClientError } from '@/api/client'
import { useAuthStore } from '@/store/authStore'
import { useSettingsStore } from '@/store/settingsStore'
import { InitialsAvatar } from '@/components/domain/InitialsAvatar'
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
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import type { Theme, UiLocale } from '@/types/auth'
import { AccentColorPicker } from './components/AccentColorPicker'
import { DEFAULT_ACCENT } from './accentPalette'

const THEME_OPTIONS: { value: Theme; label: string }[] = [
  { value: 'light', label: 'Light' },
  { value: 'dark', label: 'Dark' },
  { value: 'system', label: 'System' },
]

const LOCALE_OPTIONS: { value: UiLocale; label: string }[] = [
  { value: 'en', label: 'English' },
  { value: 'es', label: 'Español' },
]

const schema = z.object({
  displayName: z.string().trim().max(80, 'Display name must be 80 characters or fewer'),
  avatarAccentColor: z.string(),
  uiLocale: z.enum(['en', 'es']),
  theme: z.enum(['light', 'dark', 'system']),
})

type FormValues = z.infer<typeof schema>

type Banner = { variant: 'success' | 'error'; message: string }

/**
 * Global settings surface (D-102) for the current user's profile and preferences: display name,
 * avatar accent color, UI language and theme. The initials avatar previews the form values live;
 * saving persists through {@link useUpdateProfile} and reports success/failure inline (no toast,
 * D-083; no modal, D-082). The theme/language choices only persist the preference here — applying
 * the theme visually lands later (F8.7).
 */
export function UserSettingsPage() {
  const currentUser = useAuthStore((s) => s.currentUser)
  const theme = useSettingsStore((s) => s.theme)
  const uiLocale = useSettingsStore((s) => s.uiLocale)
  const { mutate: updateProfile, isPending } = useUpdateProfile()
  const [banner, setBanner] = useState<Banner | null>(null)

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      displayName: currentUser?.displayName ?? '',
      avatarAccentColor: currentUser?.avatarAccentColor ?? DEFAULT_ACCENT,
      uiLocale,
      theme,
    },
  })

  // Subscribe only to the fields that drive the live avatar preview (keeps the preview reactive
  // without re-rendering the whole form on every keystroke).
  const previewName = useWatch({ control: form.control, name: 'displayName' })
  const previewAccent = useWatch({ control: form.control, name: 'avatarAccentColor' })

  if (!currentUser) return null

  function onSubmit(values: FormValues) {
    setBanner(null)
    const trimmedName = values.displayName.trim()
    // Mirror the preference into the persisted client store immediately, matching UserMenu.
    useSettingsStore.getState().setTheme(values.theme)
    useSettingsStore.getState().setUiLocale(values.uiLocale)
    updateProfile(
      {
        displayName: trimmedName || null,
        avatarAccentColor: values.avatarAccentColor,
        uiLocale: values.uiLocale,
        theme: values.theme,
      },
      {
        onSuccess() {
          setBanner({ variant: 'success', message: 'Settings saved.' })
        },
        onError(err) {
          if (err instanceof ApiClientError) {
            let hasFieldError = false
            for (const e of err.errors) {
              if (e.field && e.field in form.getValues()) {
                form.setError(e.field as keyof FormValues, { message: e.message })
                hasFieldError = true
              }
            }
            if (!hasFieldError) {
              setBanner({
                variant: 'error',
                message: err.errors[0]?.message ?? 'Could not save settings. Please try again.',
              })
            }
          } else {
            setBanner({
              variant: 'error',
              message: 'An unexpected error occurred. Please try again.',
            })
          }
        },
      }
    )
  }

  return (
    <main className="mx-auto max-w-2xl p-8">
      <div className="mb-8 flex items-center gap-4">
        <InitialsAvatar
          displayName={previewName}
          email={currentUser.email}
          accentColor={previewAccent}
          size="lg"
        />
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Settings</h1>
          <p className="mt-1 text-sm text-slate-500">{currentUser.email}</p>
        </div>
      </div>

      {banner && (
        <div className="mb-6">
          <InlineBanner
            variant={banner.variant}
            message={banner.message}
            onDismiss={() => setBanner(null)}
          />
        </div>
      )}

      <Form {...form}>
        <form
          onSubmit={form.handleSubmit(onSubmit)}
          className="space-y-8 rounded-2xl border border-slate-200 bg-white p-8"
          noValidate
        >
          <FormField
            control={form.control}
            name="displayName"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Display name</FormLabel>
                <FormControl>
                  <Input placeholder={currentUser.email} {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="avatarAccentColor"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Avatar accent color</FormLabel>
                <FormControl>
                  <AccentColorPicker value={field.value} onChange={field.onChange} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="uiLocale"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Language</FormLabel>
                <Select onValueChange={field.onChange} value={field.value}>
                  <FormControl>
                    <SelectTrigger className="w-48">
                      <SelectValue />
                    </SelectTrigger>
                  </FormControl>
                  <SelectContent>
                    {LOCALE_OPTIONS.map((option) => (
                      <SelectItem key={option.value} value={option.value}>
                        {option.label}
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
            name="theme"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Theme</FormLabel>
                <FormControl>
                  <RadioGroup
                    value={field.value}
                    onValueChange={field.onChange}
                    className="flex flex-wrap gap-6"
                  >
                    {THEME_OPTIONS.map((option) => (
                      <label
                        key={option.value}
                        className="flex items-center gap-2 text-sm text-slate-700"
                      >
                        <RadioGroupItem value={option.value} />
                        {option.label}
                      </label>
                    ))}
                  </RadioGroup>
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          <Button type="submit" disabled={isPending} aria-disabled={isPending}>
            {isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden />}
            Save changes
          </Button>
        </form>
      </Form>
    </main>
  )
}
