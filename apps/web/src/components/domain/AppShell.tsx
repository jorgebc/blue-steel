import { Outlet } from 'react-router-dom'
import { Sidebar } from './Sidebar'

/**
 * Persistent layout for every campaign-scoped page: the {@link Sidebar} on the
 * left and the routed campaign page in the main region. Mounted as a layout
 * route inside the campaign-context guard so the shell wraps all children.
 */
export function AppShell() {
  return (
    <div className="flex min-h-screen bg-slate-50">
      <Sidebar />
      <main className="flex-1">
        <Outlet />
      </main>
    </div>
  )
}
