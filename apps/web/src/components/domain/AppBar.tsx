import { Link, useNavigate } from 'react-router-dom'
import { LogOut } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { Brand } from './Brand'

/**
 * Global top bar for every authenticated page (UX Constitution §3 "top bar"):
 * the brand links home, and the current user can log out from anywhere — not
 * only from inside a campaign.
 */
export function AppBar() {
  const navigate = useNavigate()
  const email = useAuthStore((s) => s.currentUser?.email)

  function handleLogout() {
    useAuthStore.getState().logout()
    navigate('/login')
  }

  return (
    <header className="flex h-14 shrink-0 items-center justify-between border-b border-slate-200 bg-white px-6 shadow-sm">
      <Link to="/" aria-label="Blue Steel home" className="rounded-lg">
        <Brand size="sm" />
      </Link>
      <div className="flex items-center gap-4">
        {email && <span className="hidden text-sm text-slate-600 sm:inline">{email}</span>}
        <button
          type="button"
          onClick={handleLogout}
          className="flex items-center gap-2 rounded-lg px-3 py-2 text-sm text-slate-600 transition-colors hover:bg-slate-100 hover:text-slate-900"
        >
          <LogOut className="h-4 w-4 shrink-0" aria-hidden />
          Log out
        </button>
      </div>
    </header>
  )
}
