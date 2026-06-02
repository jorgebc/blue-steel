import { NavLink, Outlet } from 'react-router-dom'
import { Clock, MapPin, Share2, Users, type LucideIcon } from 'lucide-react'

interface ViewLink {
  to: string
  label: string
  icon: LucideIcon
}

// Timeline and Relations routes mount later (F4.2.11 / F4.3.13); the links are present now so the
// nav is complete — they simply 404 within the layout until those views ship.
const VIEWS: ViewLink[] = [
  { to: 'timeline', label: 'Timeline', icon: Clock },
  { to: 'entities', label: 'Entities', icon: Users },
  { to: 'spaces', label: 'Spaces', icon: MapPin },
  { to: 'relations', label: 'Relations', icon: Share2 },
]

const activeTabClass = 'border-blue-500 text-blue-600'
const inactiveTabClass = 'border-transparent text-slate-500 hover:text-slate-900'

/** Shared shell for the four Exploration views: a view switcher plus the active view via Outlet. */
export function ExplorationLayout() {
  return (
    <main className="mx-auto max-w-5xl p-8">
      <nav aria-label="Exploration views" className="mb-6 flex gap-1 border-b border-slate-200">
        {VIEWS.map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) =>
              `flex items-center gap-2 border-b-2 px-4 py-3 text-sm font-medium transition-colors ${
                isActive ? activeTabClass : inactiveTabClass
              }`
            }
          >
            <Icon className="h-4 w-4 shrink-0" aria-hidden />
            {label}
          </NavLink>
        ))}
      </nav>

      <Outlet />
    </main>
  )
}
