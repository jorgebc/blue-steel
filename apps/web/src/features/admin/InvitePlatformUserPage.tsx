import { useState } from 'react'
import { Navigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Loader2 } from 'lucide-react'
import { useInvitePlatformUser } from '@/api/invitations'
import { ApiClientError } from '@/api/client'
import { useAuthStore } from '@/store/authStore'
import { InlineBanner } from '@/components/domain/InlineBanner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

type Banner = { variant: 'success' | 'error'; message: string } | null

/**
 * Admin-only platform invitation: creates a user account so the admin can then
 * assign them as a GM (D-051, D-026). Non-admins are redirected to the campaign
 * list. The invited user receives a temporary password by email.
 */
export function InvitePlatformUserPage() {
  const { t } = useTranslation()
  const isAdmin = useAuthStore((s) => s.currentUser?.isAdmin)
  const { mutate: invite, isPending } = useInvitePlatformUser()
  const [email, setEmail] = useState('')
  const [banner, setBanner] = useState<Banner>(null)

  if (!isAdmin) return <Navigate to="/" replace />

  function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    setBanner(null)
    const trimmed = email.trim()
    if (!trimmed) {
      setBanner({ variant: 'error', message: t('admin.enterEmail') })
      return
    }
    invite(trimmed, {
      onSuccess: () => {
        setBanner({
          variant: 'success',
          message: t('admin.invitationSent', { email: trimmed }),
        })
        setEmail('')
      },
      onError: (err) =>
        setBanner({
          variant: 'error',
          message:
            err instanceof ApiClientError
              ? (err.errors[0]?.message ?? err.message)
              : t('admin.invitationError'),
        }),
    })
  }

  return (
    <main className="mx-auto max-w-lg p-6">
      <h1 className="mb-2 text-2xl font-semibold">{t('admin.inviteUser')}</h1>
      <p className="mb-6 text-sm text-muted-foreground">{t('admin.inviteDescription')}</p>

      {banner && (
        <div className="mb-4">
          <InlineBanner
            variant={banner.variant}
            message={banner.message}
            onDismiss={() => setBanner(null)}
          />
        </div>
      )}

      <form onSubmit={onSubmit} className="space-y-4" noValidate>
        <div className="space-y-1">
          <Label htmlFor="invite-email">{t('admin.emailLabel')}</Label>
          <Input
            id="invite-email"
            type="email"
            placeholder={t('admin.emailPlaceholder')}
            autoComplete="off"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </div>
        <Button type="submit" className="w-full" disabled={isPending} aria-disabled={isPending}>
          {isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden />}
          {t('admin.sendInvitation')}
        </Button>
      </form>
    </main>
  )
}
