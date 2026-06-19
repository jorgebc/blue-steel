import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { Theme, UiLocale, UserMeResponse } from '@/types/auth'

interface SettingsState {
  theme: Theme
  uiLocale: UiLocale
  setTheme: (theme: Theme) => void
  setUiLocale: (uiLocale: UiLocale) => void
  hydrateFromUser: (me: UserMeResponse) => void
}

/**
 * Client mirror of the user's {@code theme} + {@code uiLocale}, persisted to
 * localStorage so the choice is available on first paint (no flash) before
 * {@code GET /me} resolves. Hydrated from the server user on login; the full
 * profile truth lives in {@link useAuthStore}. Defaults match the backend
 * {@code User} defaults (D-101).
 */
export const useSettingsStore = create<SettingsState>()(
  persist(
    (set) => ({
      theme: 'system',
      uiLocale: 'en',
      setTheme: (theme) => set({ theme }),
      setUiLocale: (uiLocale) => set({ uiLocale }),
      hydrateFromUser: (me) => set({ theme: me.theme, uiLocale: me.uiLocale }),
    }),
    { name: 'blue-steel-settings' }
  )
)
