import { create } from 'zustand'
import { persist } from 'zustand/middleware'

interface UiState {
  sidebarExpanded: boolean
  toggleSidebar: () => void
  setSidebarExpanded: (value: boolean) => void
}

/**
 * Client-state store for sidebar layout, persisted to localStorage so the
 * expand/collapse choice survives reloads. Components must read this store
 * rather than touching localStorage directly.
 */
export const useUiStore = create<UiState>()(
  persist(
    (set) => ({
      sidebarExpanded: true,
      toggleSidebar: () => set((s) => ({ sidebarExpanded: !s.sidebarExpanded })),
      setSidebarExpanded: (value) => set({ sidebarExpanded: value }),
    }),
    { name: 'blue-steel-sidebar' }
  )
)
