import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '@/store/authStore'
import { Brand } from './Brand'
import { UserMenu } from './UserMenu'

/**
 * Global top bar for every authenticated page (UX Constitution §3 "top bar"):
 * the brand links home, and the top-right account menu carries the user's
 * identity, settings, and logout — reachable from anywhere, not only inside a
 * campaign (D-102).
 */
export function AppBar() {
  const { t } = useTranslation()
  const isAdmin = useAuthStore((s) => s.currentUser?.isAdmin)

  return (
    <header className="flex h-14 shrink-0 items-center justify-between border-b border-border bg-surface px-6 shadow-sm">
      <Link to="/" aria-label={t('appBar.homeAriaLabel')} className="rounded-lg">
        <Brand size="sm" />
      </Link>
      <div className="flex items-center gap-4">
        {isAdmin && (
          <span className="rounded-full bg-accent-subtle px-2 py-0.5 text-xs font-medium text-accent">
            {t('appBar.admin')}
          </span>
        )}
        <UserMenu />
      </div>
    </header>
  )
}
