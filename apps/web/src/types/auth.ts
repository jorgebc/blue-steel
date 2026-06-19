export interface AuthLoginResponse {
  accessToken: string
  forcePasswordChange: boolean
}

export interface RefreshResponse {
  accessToken: string
}

export type UiLocale = 'en' | 'es'
export type Theme = 'light' | 'dark' | 'system'

export interface UserMeResponse {
  id: string
  email: string
  isAdmin: boolean
  forcePasswordChange: boolean
  displayName: string | null
  avatarAccentColor: string | null
  uiLocale: UiLocale
  theme: Theme
}

// What the authStore holds after GET /users/me succeeds
export type CurrentUser = UserMeResponse

// Body for PATCH /api/v1/users/me — every field optional (partial update)
export interface UpdateProfilePayload {
  displayName?: string | null
  avatarAccentColor?: string | null
  uiLocale?: UiLocale
  theme?: Theme
}

// A user returned by GET /api/v1/users?email= (admin/GM user search)
export interface UserSearchResult {
  id: string
  email: string
}
