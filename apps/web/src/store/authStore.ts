import { create } from 'zustand'
import type { CurrentUser } from '@/types/auth'

interface AuthState {
  accessToken: string | null
  currentUser: CurrentUser | null
  isInitializing: boolean
  setAccessToken: (token: string | null) => void
  setCurrentUser: (user: CurrentUser | null) => void
  setInitialized: () => void
  logout: () => void
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  currentUser: null,
  isInitializing: true,
  setAccessToken: (token) => set({ accessToken: token }),
  setCurrentUser: (user) => set({ currentUser: user }),
  setInitialized: () => set({ isInitializing: false }),
  logout: () => set({ accessToken: null, currentUser: null }),
}))
