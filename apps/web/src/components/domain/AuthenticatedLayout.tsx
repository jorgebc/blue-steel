import { Outlet } from 'react-router-dom'
import { AppBar } from './AppBar'

/**
 * Shell for every authenticated page: the global {@link AppBar} over the routed
 * content. Wraps the campaign list and create pages as well as the
 * campaign-scoped routes, so persistent chrome (brand, logout) is always
 * present — not only inside a campaign.
 */
export function AuthenticatedLayout() {
  return (
    <div className="flex min-h-screen flex-col bg-slate-50">
      <AppBar />
      <div className="flex flex-1 flex-col">
        <Outlet />
      </div>
    </div>
  )
}
