import { Link, NavLink } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { ChevronLeft, Compass, Home, Inbox, Library, Search, Upload } from 'lucide-react'
import { useCampaign } from '@/api/campaigns'
import { useCampaignStore } from '@/store/campaignStore'
import { useUiStore } from '@/store/uiStore'

const activeLinkClass = 'bg-accent-subtle text-accent border-r-2 border-accent font-medium'
const inactiveLinkClass = 'text-muted-foreground hover:bg-muted hover:text-foreground'
const itemBaseClass = 'flex items-center gap-3 px-4 py-3 text-sm transition-colors'

/**
 * Persistent campaign navigation: campaign switcher and role-gated mode nav
 * (Home / Input / Query / Exploration / Review queue) plus the collapse toggle.
 * Collapsed renders icons only. User-global settings live in the top-right
 * account menu, not here (D-102).
 */
export function Sidebar() {
  const { t } = useTranslation()
  const expanded = useUiStore((s) => s.sidebarExpanded)
  const toggleSidebar = useUiStore((s) => s.toggleSidebar)
  const activeCampaignId = useCampaignStore((s) => s.activeCampaignId)
  const activeRole = useCampaignStore((s) => s.activeRole)
  const { data: campaign } = useCampaign(activeCampaignId ?? undefined)

  return (
    <nav
      aria-label={t('sidebar.navAriaLabel')}
      className={`${
        expanded ? 'w-64' : 'w-16'
      } sticky top-14 flex h-[calc(100vh-3.5rem)] flex-col border-r border-border bg-surface transition-all duration-200`}
    >
      {/* Campaign switcher */}
      <div className="border-b border-border p-4">
        {expanded ? (
          <>
            <p className="truncate text-sm font-semibold text-foreground">
              {campaign?.name ?? t('sidebar.campaignFallback')}
            </p>
            {activeRole && (
              <span className="mt-0.5 self-start rounded-full bg-muted px-2 py-0.5 text-xs font-medium text-muted-foreground">
                {t(`sidebar.role.${activeRole}`)}
              </span>
            )}
            <Link to="/" className="text-xs text-accent underline-offset-4 hover:underline">
              {t('sidebar.switchCampaign')}
            </Link>
          </>
        ) : (
          <Link
            to="/"
            aria-label={t('sidebar.switchCampaign')}
            className="flex justify-center text-muted-foreground"
          >
            <Library className="h-5 w-5" aria-hidden />
          </Link>
        )}
      </div>

      {/* Mode nav */}
      <div className="flex-1 py-2">
        {activeCampaignId && (
          <NavLink
            end
            to={`/campaigns/${activeCampaignId}`}
            className={({ isActive }) =>
              `${itemBaseClass} ${isActive ? activeLinkClass : inactiveLinkClass}`
            }
          >
            <Home className="h-5 w-5 shrink-0" aria-hidden />
            {expanded && <span>{t('sidebar.home')}</span>}
          </NavLink>
        )}
        {activeRole !== 'player' && activeCampaignId && (
          <NavLink
            to={`/campaigns/${activeCampaignId}/sessions/new`}
            className={({ isActive }) =>
              `${itemBaseClass} ${isActive ? activeLinkClass : inactiveLinkClass}`
            }
          >
            <Upload className="h-5 w-5 shrink-0" aria-hidden />
            {expanded && <span>{t('sidebar.input')}</span>}
          </NavLink>
        )}
        {activeCampaignId && (
          <NavLink
            to={`/campaigns/${activeCampaignId}/query`}
            className={({ isActive }) =>
              `${itemBaseClass} ${isActive ? activeLinkClass : inactiveLinkClass}`
            }
          >
            <Search className="h-5 w-5 shrink-0" aria-hidden />
            {expanded && <span>{t('sidebar.query')}</span>}
          </NavLink>
        )}
        {activeCampaignId && (
          <NavLink
            to={`/campaigns/${activeCampaignId}/explore`}
            className={({ isActive }) =>
              `${itemBaseClass} ${isActive ? activeLinkClass : inactiveLinkClass}`
            }
          >
            <Compass className="h-5 w-5 shrink-0" aria-hidden />
            {expanded && <span>{t('sidebar.exploration')}</span>}
          </NavLink>
        )}
        {activeRole === 'gm' && activeCampaignId && (
          <NavLink
            to={`/campaigns/${activeCampaignId}/proposals`}
            className={({ isActive }) =>
              `${itemBaseClass} ${isActive ? activeLinkClass : inactiveLinkClass}`
            }
          >
            <Inbox className="h-5 w-5 shrink-0" aria-hidden />
            {expanded && <span>{t('sidebar.reviewQueue')}</span>}
          </NavLink>
        )}
      </div>

      {/* Collapse toggle */}
      <div className="border-t border-border p-2">
        <button
          type="button"
          onClick={toggleSidebar}
          aria-label={expanded ? t('sidebar.collapse') : t('sidebar.expand')}
          className="flex w-full items-center justify-center rounded-lg p-2 text-muted-foreground hover:bg-muted"
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
