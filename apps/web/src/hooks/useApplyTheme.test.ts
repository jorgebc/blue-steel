import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useApplyTheme } from './useApplyTheme'
import { useSettingsStore } from '@/store/settingsStore'

type MediaListener = (e: { matches: boolean }) => void

/**
 * Installs a controllable `window.matchMedia` whose `matches` can be flipped at
 * runtime, firing registered `change` listeners — so the `system` follow path
 * can be exercised. Returns a setter for the OS preference.
 */
function mockMatchMedia(initialMatches: boolean) {
  let matches = initialMatches
  const listeners = new Set<MediaListener>()
  const mql = {
    get matches() {
      return matches
    },
    media: '(prefers-color-scheme: dark)',
    onchange: null,
    addEventListener: (_: string, l: MediaListener) => listeners.add(l),
    removeEventListener: (_: string, l: MediaListener) => listeners.delete(l),
    addListener: (l: MediaListener) => listeners.add(l),
    removeListener: (l: MediaListener) => listeners.delete(l),
    dispatchEvent: () => true,
  }
  window.matchMedia = vi.fn().mockReturnValue(mql) as unknown as typeof window.matchMedia
  return {
    setMatches(next: boolean) {
      matches = next
      act(() => listeners.forEach((l) => l({ matches })))
    },
  }
}

describe('useApplyTheme', () => {
  beforeEach(() => {
    document.documentElement.classList.remove('dark')
    useSettingsStore.setState({ theme: 'system' })
  })

  afterEach(() => {
    useSettingsStore.setState({ theme: 'system' })
  })

  it("adds the `dark` class when theme is 'dark'", () => {
    mockMatchMedia(false)
    useSettingsStore.setState({ theme: 'dark' })

    renderHook(() => useApplyTheme())

    expect(document.documentElement.classList.contains('dark')).toBe(true)
  })

  it("removes the `dark` class when theme is 'light'", () => {
    mockMatchMedia(true)
    document.documentElement.classList.add('dark')
    useSettingsStore.setState({ theme: 'light' })

    renderHook(() => useApplyTheme())

    expect(document.documentElement.classList.contains('dark')).toBe(false)
  })

  it("applies the OS preference when theme is 'system'", () => {
    mockMatchMedia(true)
    useSettingsStore.setState({ theme: 'system' })

    renderHook(() => useApplyTheme())

    expect(document.documentElement.classList.contains('dark')).toBe(true)
  })

  it("reacts live to an OS change while theme is 'system'", () => {
    const media = mockMatchMedia(true)
    useSettingsStore.setState({ theme: 'system' })

    renderHook(() => useApplyTheme())
    expect(document.documentElement.classList.contains('dark')).toBe(true)

    media.setMatches(false)

    expect(document.documentElement.classList.contains('dark')).toBe(false)
  })

  it('reacts when the stored theme changes without a remount', () => {
    mockMatchMedia(false)
    useSettingsStore.setState({ theme: 'light' })

    renderHook(() => useApplyTheme())
    expect(document.documentElement.classList.contains('dark')).toBe(false)

    act(() => useSettingsStore.setState({ theme: 'dark' }))

    expect(document.documentElement.classList.contains('dark')).toBe(true)
  })
})
