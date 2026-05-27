import { Navigate, useLocation } from 'react-router-dom'
import { useAuthStore } from '@/store/authStore'

interface Props {
  children: React.ReactNode
}

export function RequireAuth({ children }: Props) {
  const accessToken = useAuthStore((s) => s.accessToken)
  const currentUser = useAuthStore((s) => s.currentUser)
  const location = useLocation()

  if (!accessToken) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  if (currentUser?.forcePasswordChange && location.pathname !== '/change-password') {
    return <Navigate to="/change-password" replace />
  }

  return <>{children}</>
}
