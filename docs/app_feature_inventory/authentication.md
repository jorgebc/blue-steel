# Module Name: Authentication

## 1. Overview

Authentication controls access to the entire application. Blue Steel is invitation-only: there is no self-registration. Users sign in with email and password and receive a short-lived JWT access token (15 minutes) plus a rotating 30-day refresh token stored in an httpOnly cookie. The frontend keeps the access token in memory only and silently refreshes it when it expires, so users stay logged in without re-entering credentials. Invited users arrive with a temporary password and are forced to set their own password before using the app.

A deliberate design property (D-043): the JWT carries only the user ID and the platform-admin flag. Campaign-level roles (GM/Editor/Player) are **never** embedded in the token — they are resolved from the database on every request, so role changes take effect immediately.

## 2. Capabilities & Use Cases

- **Use Case / Action:** User signs in with email and password — ✅ Implemented
- **Actor:** Anonymous User
- **Functional Description:** Validates credentials and, on success, issues an access JWT and sets a rotating refresh-token cookie. The response includes a `forcePasswordChange` flag; when set, the frontend redirects the user to the password-change screen before anything else. Invalid credentials show an inline error banner on the login form.
- **Technical Reference / Source Files:** `POST /api/v1/auth/login` — `apps/api/src/main/java/com/bluesteel/adapters/in/web/auth/AuthController.java`, `apps/api/src/main/java/com/bluesteel/application/service/auth/LoginService.java`, `apps/web/src/features/auth/LoginPage.tsx`

---

- **Use Case / Action:** Client silently refreshes an expired access token — ✅ Implemented
- **Actor:** System (frontend HTTP client), on behalf of an Authenticated User
- **Functional Description:** When any API call returns 401, the frontend automatically calls the refresh endpoint once (using the httpOnly cookie) and retries the original request. The backend rotates the refresh token and detects token reuse via token families (a replayed old token invalidates the whole family — stolen-cookie defence). If refresh also fails, the user is redirected to the login page.
- **Technical Reference / Source Files:** `POST /api/v1/auth/refresh` — `AuthController.java`, `apps/api/src/main/java/com/bluesteel/application/service/auth/RefreshTokenService.java`, `apps/web/src/api/client.ts`

---

- **Use Case / Action:** User logs out — ✅ Implemented
- **Actor:** Authenticated User
- **Functional Description:** Revokes the refresh token server-side, clears the cookie, and clears the in-memory access token and user state in the frontend. The logout control is always available in the top application bar.
- **Technical Reference / Source Files:** `POST /api/v1/auth/logout` — `AuthController.java`, `apps/api/src/main/java/com/bluesteel/application/service/auth/LogoutService.java`, `apps/web/src/components/domain/AppBar.tsx`, `apps/web/src/store/authStore.ts`

---

- **Use Case / Action:** Invited user is forced to change a temporary password on first login — ✅ Implemented
- **Actor:** Authenticated User (newly invited)
- **Functional Description:** Users created through an invitation carry a `forcePasswordChange` flag. After login the route guard redirects them to `/change-password`; they must provide the current (temporary) password and a new password of at least 12 characters before reaching the rest of the app. (The password-change endpoint itself is documented in [user_management.md](user_management.md).)
- **Technical Reference / Source Files:** `apps/web/src/features/auth/ChangePasswordPage.tsx`, `apps/web/src/components/domain/RequireAuth.tsx`, `apps/api/src/main/java/com/bluesteel/application/service/user/ChangePasswordService.java`

---

- **Use Case / Action:** Every protected request is authenticated and security-hardened — ✅ Implemented
- **Actor:** System (security filter chain)
- **Functional Description:** A stateless JWT filter validates the bearer token on every request; all routes except `/api/v1/health` and `/api/v1/auth/**` require authentication. Responses carry OWASP hardening headers (HSTS, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, Referrer-Policy, Permissions-Policy). CORS allowed origins are environment-configured. Passwords are hashed with BCrypt.
- **Technical Reference / Source Files:** `apps/api/src/main/java/com/bluesteel/adapters/in/security/SecurityConfig.java`, `apps/api/src/main/java/com/bluesteel/adapters/in/security/JwtAuthenticationFilter.java`

---

- **Use Case / Action:** Frontend route guarding by auth state and admin flag — ✅ Implemented
- **Actor:** System (frontend router)
- **Functional Description:** `RequireAuth` wraps all authenticated routes and redirects anonymous visitors to `/login` (and force-password-change users to `/change-password`). Admin-only pages (`/campaigns/new`, `/invite`) check `isAdmin` and redirect non-admins away. Campaign-scoped routes load campaign context and the caller's role before rendering.
- **Technical Reference / Source Files:** `apps/web/src/components/domain/RequireAuth.tsx`, `apps/web/src/components/domain/CampaignContextGuard.tsx`, `apps/web/src/main.tsx`

## 3. Core User Journeys (Workflows)

**Journey: Invited user onboarding (invitation → first session)**
1. Admin or GM invites the user (see [user_management.md](user_management.md) / [campaign_management.md](campaign_management.md)); the user receives an email with a temporary password.
2. User opens the app → is redirected to `/login` → signs in with email + temporary password.
3. Backend responds with `forcePasswordChange: true`; the frontend redirects to `/change-password`.
4. User sets a new password (min 12 chars, must differ from the temporary one); the flag is cleared.
5. User lands on the campaign list (`/`) showing the campaigns they belong to, with their role badge.

**Journey: Transparent session continuity**
1. User works in the app; after 15 minutes the access token expires.
2. The next API call returns 401; the HTTP client calls `POST /auth/refresh` using the httpOnly cookie.
3. Backend rotates the refresh token (family tracked for reuse detection) and returns a fresh JWT; the original request is retried — the user notices nothing.
4. If the refresh token is expired or revoked, the user is returned to `/login`.
