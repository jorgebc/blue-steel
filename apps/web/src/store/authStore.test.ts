import { describe, it, expect, beforeEach } from 'vitest'
import { useAuthStore } from './authStore'
import type { CurrentUser } from '@/types/auth'

const mockUser: CurrentUser = {
  id: 'user-1',
  email: 'gm@example.com',
  isAdmin: false,
  forcePasswordChange: false,
  displayName: null,
  avatarAccentColor: null,
  uiLocale: 'en',
  theme: 'system',
}

describe('authStore', () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: null, currentUser: null, isInitializing: false })
  })

  it('setAccessToken stores the token', () => {
    useAuthStore.getState().setAccessToken('tok-abc')
    expect(useAuthStore.getState().accessToken).toBe('tok-abc')
  })

  it('setAccessToken(null) clears the token', () => {
    useAuthStore.setState({ accessToken: 'tok-abc' })
    useAuthStore.getState().setAccessToken(null)
    expect(useAuthStore.getState().accessToken).toBeNull()
  })

  it('setCurrentUser stores the user', () => {
    useAuthStore.getState().setCurrentUser(mockUser)
    expect(useAuthStore.getState().currentUser).toEqual(mockUser)
  })

  it('logout clears accessToken and currentUser', () => {
    useAuthStore.setState({ accessToken: 'tok-abc', currentUser: mockUser })
    useAuthStore.getState().logout()
    expect(useAuthStore.getState().accessToken).toBeNull()
    expect(useAuthStore.getState().currentUser).toBeNull()
  })

  it('isInitializing is true on initial store state', () => {
    useAuthStore.setState({ isInitializing: true })
    expect(useAuthStore.getState().isInitializing).toBe(true)
  })

  it('setInitialized sets isInitializing to false', () => {
    useAuthStore.setState({ isInitializing: true })
    useAuthStore.getState().setInitialized()
    expect(useAuthStore.getState().isInitializing).toBe(false)
  })
})
