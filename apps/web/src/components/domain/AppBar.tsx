import { Link } from 'react-router-dom'
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
  const isAdmin = useAuthStore((s) => s.currentUser?.isAdmin)

  return (
    <header className="flex h-14 shrink-0 items-center justify-between border-b border-slate-200 bg-white px-6 shadow-sm">
      <Link to="/" aria-label="Blue Steel home" className="rounded-lg">
        <Brand size="sm" />
      </Link>
      <div className="flex items-center gap-4">
        {isAdmin && (
          <span className="rounded-full bg-blue-100 px-2 py-0.5 text-xs font-medium text-blue-700">
            Admin
          </span>
        )}
        <UserMenu />
      </div>
    </header>
  )
}
