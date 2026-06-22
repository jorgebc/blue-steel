import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useTranslation } from 'react-i18next'
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

const THEME_OPTIONS: { value: Theme; labelKey: string }[] = [
  { value: 'light', labelKey: 'userMenu.themeLight' },
  { value: 'dark', labelKey: 'userMenu.themeDark' },
  { value: 'system', labelKey: 'userMenu.themeSystem' },
]

// Language names are intentionally shown in their own language, never translated (matches UserMenu).
const LOCALE_OPTIONS: { value: UiLocale; label: string }[] = [
  { value: 'en', label: 'English' },
  { value: 'es', label: 'Español' },
]

type FormValues = {
  displayName: string
  avatarAccentColor: string
  uiLocale: UiLocale
  theme: Theme
}

type Banner = { variant: 'success' | 'error'; message: string }

/**
 * Global settings surface (D-102) for the current user's profile and preferences: display name,
 * avatar accent color, UI language and theme. The initials avatar previews the form values live;
 * saving persists through {@link useUpdateProfile} and reports success/failure inline (no toast,
 * D-083; no modal, D-082). The theme/language choices only persist the preference here — applying
 * the theme visually lands later (F8.7).
 */
export function UserSettingsPage() {
  const { t } = useTranslation()
  const currentUser = useAuthStore((s) => s.currentUser)
  const theme = useSettingsStore((s) => s.theme)
  const uiLocale = useSettingsStore((s) => s.uiLocale)
  const { mutate: updateProfile, isPending } = useUpdateProfile()
  const [banner, setBanner] = useState<Banner | null>(null)

  const form = useForm<FormValues>({
    resolver: zodResolver(
      z.object({
        displayName: z.string().trim().max(80, t('settings.displayNameMax')),
        avatarAccentColor: z.string(),
        uiLocale: z.enum(['en', 'es']),
        theme: z.enum(['light', 'dark', 'system']),
      })
    ),
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
        // Empty string is the backend's explicit "clear display name" sentinel; null would mean
        // "leave unchanged" under the PATCH partial-merge contract.
        displayName: trimmedName,
        avatarAccentColor: values.avatarAccentColor,
        uiLocale: values.uiLocale,
        theme: values.theme,
      },
      {
        onSuccess() {
          setBanner({ variant: 'success', message: t('settings.settingsSaved') })
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
                message: err.errors[0]?.message ?? t('settings.saveError'),
              })
            }
          } else {
            setBanner({
              variant: 'error',
              message: t('common.unexpectedError'),
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
          <h1 className="text-2xl font-semibold text-foreground">{t('settings.title')}</h1>
          <p className="mt-1 text-sm text-muted-foreground">{currentUser.email}</p>
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
          className="space-y-8 rounded-2xl border border-border bg-surface p-8"
          noValidate
        >
          <FormField
            control={form.control}
            name="displayName"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t('settings.displayName')}</FormLabel>
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
                <FormLabel>{t('settings.avatarAccentColor')}</FormLabel>
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
                <FormLabel>{t('settings.language')}</FormLabel>
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
                <FormLabel>{t('settings.theme')}</FormLabel>
                <FormControl>
                  <RadioGroup
                    value={field.value}
                    onValueChange={field.onChange}
                    className="flex flex-wrap gap-6"
                  >
                    {THEME_OPTIONS.map((option) => (
                      <label
                        key={option.value}
                        className="flex items-center gap-2 text-sm text-foreground"
                      >
                        <RadioGroupItem value={option.value} />
                        {t(option.labelKey)}
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
            {t('settings.saveChanges')}
          </Button>
        </form>
      </Form>
    </main>
  )
}
