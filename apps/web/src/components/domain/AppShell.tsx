import { Outlet } from 'react-router-dom'
import { Sidebar } from './Sidebar'

/**
 * Campaign-scoped layout nested under the authenticated shell: the
 * {@link Sidebar} on the left and the routed campaign page beside it. The page
 * background and top bar come from {@link AuthenticatedLayout}; each routed page
 * owns its own {@code <main>} landmark.
 */
export function AppShell() {
  return (
    <div className="flex flex-1">
      <Sidebar />
      <div className="flex-1">
        <Outlet />
      </div>
    </div>
  )
}
