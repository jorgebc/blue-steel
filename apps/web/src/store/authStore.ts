import { create } from 'zustand'
import type { CurrentUser } from '@/types/auth'

interface AuthState {
  accessToken: string | null
  currentUser: CurrentUser | null
  setAccessToken: (token: string | null) => void
  setCurrentUser: (user: CurrentUser | null) => void
  logout: () => void
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  currentUser: null,
  setAccessToken: (token) => set({ accessToken: token }),
  setCurrentUser: (user) => set({ currentUser: user }),
  logout: () => set({ accessToken: null, currentUser: null }),
}))
