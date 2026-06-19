import { describe, it, expect, beforeEach } from 'vitest'
import { useSettingsStore } from './settingsStore'
import type { UserMeResponse } from '@/types/auth'

const user: UserMeResponse = {
  id: 'u1',
  email: 'gm@example.com',
  isAdmin: false,
  forcePasswordChange: false,
  displayName: 'Game Master',
  avatarAccentColor: '#3b82f6',
  uiLocale: 'es',
  theme: 'dark',
}

describe('settingsStore', () => {
  beforeEach(() => {
    localStorage.clear()
    useSettingsStore.setState({ theme: 'system', uiLocale: 'en' })
  })

  it('defaults theme to system and uiLocale to en', () => {
    expect(useSettingsStore.getState().theme).toBe('system')
    expect(useSettingsStore.getState().uiLocale).toBe('en')
  })

  it('setTheme updates the theme', () => {
    useSettingsStore.getState().setTheme('light')
    expect(useSettingsStore.getState().theme).toBe('light')
  })

  it('setUiLocale updates the locale', () => {
    useSettingsStore.getState().setUiLocale('es')
    expect(useSettingsStore.getState().uiLocale).toBe('es')
  })

  it('hydrateFromUser populates theme and uiLocale from the user', () => {
    useSettingsStore.getState().hydrateFromUser(user)
    expect(useSettingsStore.getState().theme).toBe('dark')
    expect(useSettingsStore.getState().uiLocale).toBe('es')
  })

  it('persists theme and uiLocale under the blue-steel-settings localStorage key', () => {
    useSettingsStore.getState().hydrateFromUser(user)
    const persisted = JSON.parse(localStorage.getItem('blue-steel-settings') as string)
    expect(persisted.state.theme).toBe('dark')
    expect(persisted.state.uiLocale).toBe('es')
  })
})
