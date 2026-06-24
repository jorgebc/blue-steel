import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useTranslation } from 'react-i18next'
import { Loader2 } from 'lucide-react'
import { useChangePassword } from '@/api/users'
import { ApiClientError } from '@/api/client'
import { useAuthStore } from '@/store/authStore'
import { Brand } from '@/components/domain/Brand'
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

type FormValues = { currentPassword: string; newPassword: string }

export function ChangePasswordPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { mutate: changePassword, isPending } = useChangePassword()
  const [banner, setBanner] = useState<string | null>(null)

  const form = useForm<FormValues>({
    resolver: zodResolver(
      z.object({
        currentPassword: z.string().min(1, t('auth.currentPasswordRequired')),
        newPassword: z.string().min(12, t('auth.newPasswordMinLength')),
      })
    ),
    defaultValues: { currentPassword: '', newPassword: '' },
  })

  function onSubmit(values: FormValues) {
    setBanner(null)
    changePassword(values, {
      onSuccess() {
        const current = useAuthStore.getState().currentUser
        if (current) {
          useAuthStore.getState().setCurrentUser({ ...current, forcePasswordChange: false })
        }
        navigate('/', { replace: true })
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
            setBanner(err.errors[0]?.message ?? t('auth.passwordChangeFailed'))
          }
        } else {
          setBanner(t('common.unexpectedError'))
        }
      },
    })
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-background p-8">
      <div className="w-full max-w-sm">
        <div className="mb-8 flex justify-center">
          <Brand size="lg" />
        </div>
        <div className="rounded-2xl bg-surface p-8 shadow-sm">
          <h1 className="mb-6 text-2xl font-semibold text-foreground">
            {t('auth.changePassword')}
          </h1>
          {banner && (
            <div className="mb-4">
              <InlineBanner variant="error" message={banner} onDismiss={() => setBanner(null)} />
            </div>
          )}
          <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4" noValidate>
              <FormField
                control={form.control}
                name="currentPassword"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t('auth.currentPassword')}</FormLabel>
                    <FormControl>
                      <Input
                        type="password"
                        placeholder={t('auth.passwordPlaceholder')}
                        autoComplete="current-password"
                        {...field}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="newPassword"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t('auth.newPassword')}</FormLabel>
                    <FormControl>
                      <Input
                        type="password"
                        placeholder={t('auth.newPasswordPlaceholder')}
                        autoComplete="new-password"
                        {...field}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <Button
                type="submit"
                className="w-full"
                disabled={isPending}
                aria-disabled={isPending}
              >
                {isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden />}
                {t('auth.changePassword')}
              </Button>
            </form>
          </Form>
        </div>
      </div>
    </div>
  )
}
