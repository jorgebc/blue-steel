export interface AuthLoginResponse {
  accessToken: string
  forcePasswordChange: boolean
}

export interface RefreshResponse {
  accessToken: string
}

export interface UserMeResponse {
  id: string
  email: string
  isAdmin: boolean
  forcePasswordChange: boolean
}

// What the authStore holds after GET /users/me succeeds
export type CurrentUser = UserMeResponse
