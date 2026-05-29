import { describe, it, expect, beforeEach } from 'vitest'
import { useUiStore } from './uiStore'

describe('uiStore', () => {
  beforeEach(() => {
    localStorage.clear()
    useUiStore.setState({ sidebarExpanded: true })
  })

  it('defaults sidebarExpanded to true', () => {
    expect(useUiStore.getState().sidebarExpanded).toBe(true)
  })

  it('toggleSidebar flips sidebarExpanded', () => {
    useUiStore.getState().toggleSidebar()
    expect(useUiStore.getState().sidebarExpanded).toBe(false)
    useUiStore.getState().toggleSidebar()
    expect(useUiStore.getState().sidebarExpanded).toBe(true)
  })

  it('setSidebarExpanded sets the value explicitly', () => {
    useUiStore.getState().setSidebarExpanded(false)
    expect(useUiStore.getState().sidebarExpanded).toBe(false)
  })

  it('persists sidebar state under the blue-steel-sidebar localStorage key', () => {
    useUiStore.getState().setSidebarExpanded(false)
    const persisted = JSON.parse(localStorage.getItem('blue-steel-sidebar') as string)
    expect(persisted.state.sidebarExpanded).toBe(false)
  })
})
