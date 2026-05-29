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

// A user returned by GET /api/v1/users?email= (admin/GM user search)
export interface UserSearchResult {
  id: string
  email: string
}
