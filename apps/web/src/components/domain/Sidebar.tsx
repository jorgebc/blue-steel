import { Link, NavLink } from 'react-router-dom'
import {
  ChevronLeft,
  Compass,
  Library,
  Search,
  Settings,
  Upload,
  type LucideIcon,
} from 'lucide-react'
import { useCampaign } from '@/api/campaigns'
import { useCampaignStore } from '@/store/campaignStore'
import { useUiStore } from '@/store/uiStore'
import type { CampaignRole } from '@/types/campaign'

const ROLE_LABEL: Record<CampaignRole, string> = {
  gm: 'GM',
  editor: 'Editor',
  player: 'Player',
}

const activeLinkClass = 'bg-blue-50 text-blue-600 border-r-2 border-blue-500 font-medium'
const inactiveLinkClass = 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
const itemBaseClass = 'flex items-center gap-3 px-4 py-3 text-sm transition-colors'

/** A mode-nav entry whose feature has not shipped yet — visible but inert. */
function ComingSoonItem({
  label,
  icon: Icon,
  expanded,
}: {
  label: string
  icon: LucideIcon
  expanded: boolean
}) {
  return (
    <div
      aria-disabled="true"
      title="Coming soon"
      className={`${itemBaseClass} cursor-not-allowed text-slate-400`}
    >
      <Icon className="h-5 w-5 shrink-0" aria-hidden />
      {expanded && (
        <span className="flex flex-1 items-center justify-between">
          {label}
          <span className="text-xs text-slate-400">Coming soon</span>
        </span>
      )}
    </div>
  )
}

/**
 * Persistent campaign navigation: campaign switcher, role-gated mode nav
 * (Input live; Query/Exploration/Settings stubbed), collapse toggle, and the
 * current user with logout. Collapsed renders icons only.
 */
export function Sidebar() {
  const expanded = useUiStore((s) => s.sidebarExpanded)
  const toggleSidebar = useUiStore((s) => s.toggleSidebar)
  const activeCampaignId = useCampaignStore((s) => s.activeCampaignId)
  const activeRole = useCampaignStore((s) => s.activeRole)
  const { data: campaign } = useCampaign(activeCampaignId ?? undefined)

  return (
    <nav
      aria-label="Campaign navigation"
      className={`${
        expanded ? 'w-64' : 'w-16'
      } sticky top-14 flex h-[calc(100vh-3.5rem)] flex-col border-r border-slate-200 bg-white transition-all duration-200`}
    >
      {/* Campaign switcher */}
      <div className="border-b border-slate-200 p-4">
        {expanded ? (
          <>
            <p className="truncate text-sm font-semibold text-slate-900">
              {campaign?.name ?? 'Campaign'}
            </p>
            {activeRole && (
              <span className="mt-0.5 self-start rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600">
                {ROLE_LABEL[activeRole]}
              </span>
            )}
            <Link to="/" className="text-xs text-blue-500 underline-offset-4 hover:underline">
              Switch campaign
            </Link>
          </>
        ) : (
          <Link to="/" aria-label="Switch campaign" className="flex justify-center text-slate-600">
            <Library className="h-5 w-5" aria-hidden />
          </Link>
        )}
      </div>

      {/* Mode nav */}
      <div className="flex-1 py-2">
        {activeRole !== 'player' && activeCampaignId && (
          <NavLink
            to={`/campaigns/${activeCampaignId}/sessions/new`}
            className={({ isActive }) =>
              `${itemBaseClass} ${isActive ? activeLinkClass : inactiveLinkClass}`
            }
          >
            <Upload className="h-5 w-5 shrink-0" aria-hidden />
            {expanded && <span>Input</span>}
          </NavLink>
        )}
        <ComingSoonItem label="Query" icon={Search} expanded={expanded} />
        <ComingSoonItem label="Exploration" icon={Compass} expanded={expanded} />
        <ComingSoonItem label="Settings" icon={Settings} expanded={expanded} />
      </div>

      {/* Collapse toggle */}
      <div className="border-t border-slate-200 p-2">
        <button
          type="button"
          onClick={toggleSidebar}
          aria-label={expanded ? 'Collapse sidebar' : 'Expand sidebar'}
          className="flex w-full items-center justify-center rounded-lg p-2 text-slate-500 hover:bg-slate-100"
        >
          <ChevronLeft
            className={`h-4 w-4 transition-transform duration-200 ${expanded ? '' : 'rotate-180'}`}
            aria-hidden
          />
        </button>
      </div>
    </nav>
  )
}
