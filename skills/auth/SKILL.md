---
name: auth
description: >
  Use this skill whenever you are working on authentication or authorization in either
  `apps/api` or `apps/web`. Triggers include: "login", "logout", "JWT", "access token",
  "refresh token", "Spring Security", "SecurityConfig", "JwtAuthenticationFilter", "route
  guard", "auth store", "silent refresh", "token expiry", "admin bootstrap", "invitation",
  "user creation", "campaign role", "is_admin", or any task touching the auth system on
  either side of the API boundary. This skill covers both backend (JWT issuance, refresh
  token rotation, Spring Security filter chain, admin singleton, user bootstrap) and
  frontend (in-memory token storage, silent refresh, route guards, role derivation).
---

# Authentication & Authorization

Blue Steel uses stateless JWT authentication (HS256, 15-minute access token TTL) with
rotating refresh tokens (30-day TTL, `httpOnly` cookie). Campaign-level role is never
embedded in the JWT — it is resolved on every request from the database (D-043). The
`admin` role is a singleton enforced at the DB level (D-025).

## Context

**Key decisions:**
- D-043: JWT carries only `user_id` and `is_admin`; campaign role resolved from `campaign_members` via DB on every authorized request
- D-025: Singleton admin — exactly one `is_admin = TRUE` user enforced by partial unique index
- D-051: Self-registration not supported — all user accounts are created via admin invitation
- D-059: Refresh token rotation with family-based reuse detection (reuse of a consumed token revokes the entire family)
- D-070: Self-service password reset not implemented in v1

**Token TTLs:**
- Access token: 15 minutes (HS256 JWT, stored in memory on the frontend)
- Refresh token: 30 days (random 256-bit token, stored as `httpOnly` cookie; SHA-256 hash stored in DB)

---

## Backend — JWT Issuance and Validation

### Spring Security filter chain

`SecurityConfig` (in `adapters/in/security/`) defines the filter chain. Key configuration:

- Stateless session management (`SessionCreationPolicy.STATELESS`)
- `JwtAuthenticationFilter` runs before `UsernamePasswordAuthenticationFilter`
- Public routes: `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`
- All other routes require a valid JWT

```java
// adapters/in/security/SecurityConfig.java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                            JwtAuthenticationFilter jwtFilter) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
```

### JWT authentication filter

`JwtAuthenticationFilter` validates the `Authorization: Bearer <token>` header and populates
the `SecurityContext` on every request:

```java
// adapters/in/security/JwtAuthenticationFilter.java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtPort jwtPort;  // driven port — validates and parses JWT

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }
        String token = header.substring(7);
        try {
            JwtClaims claims = jwtPort.validate(token);
            // Set SecurityContext — controllers extract userId and isAdmin from here
            UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                    claims.userId(), null,
                    claims.isAdmin() ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN")) : List.of()
                );
            auth.setDetails(claims);
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (JwtValidationException e) {
            // Do not set SecurityContext — request proceeds as unauthenticated;
            // Spring Security will return 401 for protected routes
        }
        chain.doFilter(request, response);
    }
}
```

### JWT port (driven port)

```java
// application/port/out/JwtPort.java
public interface JwtPort {
    String issue(UUID userId, boolean isAdmin, Duration ttl);
    JwtClaims validate(String token);  // throws JwtValidationException if invalid/expired
}

// domain/auth/JwtClaims.java
public record JwtClaims(UUID userId, boolean isAdmin, Instant expiresAt) {}
```

The adapter in `adapters/out/ai/` (or a dedicated `adapters/out/security/`) implements this
using a Java JWT library (e.g., `jjwt`). The `JWT_SECRET` is loaded from environment config
and injected via `@Value("${jwt.secret}")`.

---

## Backend — Refresh Token Rotation (D-059)

Refresh tokens use a family-based rotation model to detect token reuse:

1. On login: issue a new refresh token `family_id` (UUID). Store `SHA-256(rawToken)` in
   `refresh_tokens`. Return the raw token as a `httpOnly` cookie.
2. On refresh: find the token by hash. If it is `consumed`, the entire family is compromised —
   revoke all tokens in the family and return `401`. If valid: mark the current token as
   `consumed`, issue a new access token + new refresh token in the same `family_id`, store the
   new token hash, return 200.
3. On logout: mark the current refresh token as `revoked`; invalidate the cookie.

```java
// domain/auth/RefreshToken.java
public class RefreshToken {
    private final UUID id;
    private final UUID userId;
    private final UUID familyId;            // all rotations in a session share a family
    private final String tokenHash;         // SHA-256 of raw token — raw never stored
    private final RefreshTokenStatus status; // ACTIVE | CONSUMED | REVOKED
    private final Instant expiresAt;
    private final Instant createdAt;

    // Invariant: raw token is never stored — only the hash
    public static RefreshToken create(UUID userId, UUID familyId, String rawToken) {
        return new RefreshToken(UUID.randomUUID(), userId, familyId,
            sha256(rawToken), RefreshTokenStatus.ACTIVE, Instant.now().plus(30, DAYS), Instant.now());
    }

    public RefreshToken consume() {
        if (this.status != RefreshTokenStatus.ACTIVE) {
            throw new DomainException("Cannot consume a non-active refresh token");
        }
        return new RefreshToken(id, userId, familyId, tokenHash,
            RefreshTokenStatus.CONSUMED, expiresAt, createdAt);
    }
}
```

**Refresh endpoint flow:**

```
POST /api/v1/auth/refresh
  (refresh token in httpOnly cookie)
  → look up token by SHA-256 hash
  → if not found or expired → 401
  → if CONSUMED → revoke entire family → 401 (reuse attack detected)
  → if ACTIVE → consume current token
                → issue new access JWT (15 min)
                → create new refresh token (same family_id, 30 days)
                → return new access token in body
                → set new refresh token as httpOnly cookie
```

---

## Backend — Authorization at the Use-Case Boundary (AUTH-01)

Controllers extract identity from `SecurityContext`. Campaign-level role is always resolved
from the database at the use-case boundary — never from JWT claims.

```java
// In any campaign-scoped use-case service
public void someProtectedAction(UUID campaignId, UUID callerId, ...) {
    CampaignRole role = campaignMembershipPort.resolveRole(campaignId, callerId);
    if (role != CampaignRole.GM) {
        throw new UnauthorizedException("Only GMs may perform this action");
    }
    // proceed
}
```

**Role hierarchy for authorization decisions:**

| Who can do what | admin | gm | editor | player |
|---|---|---|---|---|
| Create campaigns | ✅ | — | — | — |
| Submit sessions / commit | — | ✅ | ✅ | — |
| Query Mode | ✅ | ✅ | ✅ | ✅ |
| Exploration Mode | ✅ | ✅ | ✅ | ✅ |
| Post annotations | — | ✅ | ✅ | ✅ |
| Delete any annotation | — | ✅ | — | — |
| Delete own annotation | — | ✅ | ✅ | ✅ |
| Discard draft session | — | ✅ | — | — |

---

## Backend — Admin Singleton and Bootstrap (D-025)

The `admin` role is a singleton: exactly one user may have `is_admin = TRUE`. This is enforced
by a partial unique index in the database:

```xml
<!-- In Liquibase changeset for users table -->
<sql>
    CREATE UNIQUE INDEX uidx_users_singleton_admin
    ON users (is_admin)
    WHERE is_admin = TRUE;
</sql>
```

**Admin bootstrap — first-run seed:**

The initial admin account is created via a Liquibase `<loadData>` changeset or a dedicated
seed script that runs on first startup. It should check for the existence of an admin before
inserting to avoid changeset replay failures:

```xml
<!-- db/changelog/0001_seed_admin_user.xml -->
<changeSet id="0001-seed-admin" author="jorge" runOnChange="false">
    <preConditions onFail="MARK_RAN">
        <!-- Only run if no admin user exists yet -->
        <sqlCheck expectedResult="0">SELECT COUNT(*) FROM users WHERE is_admin = TRUE</sqlCheck>
    </preConditions>
    <sql>
        INSERT INTO users (id, email, password_hash, is_admin, created_at)
        VALUES (gen_random_uuid(), '${adminEmail}', '${adminPasswordHash}', TRUE, NOW());
    </sql>
</changeSet>
```

Provide `adminEmail` and `adminPasswordHash` as Liquibase parameters from the environment.
**Never commit real credentials.** The password hash should be a bcrypt hash of the initial
admin password set via environment variable at first deploy.

---

## Backend — User Invitation Flow (D-051)

Self-registration is disabled. All accounts are created by the admin via invitation. The
invitation flow in v1 is:

1. Admin calls `POST /api/v1/admin/invitations` with `{ email, campaignId?, role? }`.
2. Backend generates a one-time invitation token (UUID, 48h TTL) and stores it in
   `invitations` table.
3. Backend sends an invitation email via the email adapter (`EmailPort`). The email contains
   a link with the token: `<frontend-url>/accept-invite?token=<token>`.
4. User visits the link, sets a password. Frontend calls `POST /api/v1/auth/accept-invitation`
   with `{ token, password }`.
5. Backend validates the token, creates the user account, returns a JWT + refresh token.

> ⚠️ The email provider (Resend recommended) and `EmailPort` implementation details are
> finalized in Phase 1. The port interface is defined now; the adapter is wired in Phase 1.

```java
// application/port/out/EmailPort.java
public interface EmailPort {
    void sendInvitation(String recipientEmail, String invitationToken, String frontendBaseUrl);
}
```

---

## Frontend — Token Storage and Silent Refresh

### In-memory token storage

The JWT access token is stored exclusively in the Zustand auth store — never `localStorage`
or `sessionStorage`. Refresh tokens are `httpOnly` cookies managed by the browser.

```typescript
// src/store/authStore.ts
import { create } from 'zustand';

interface AuthState {
  accessToken: string | null;
  userId: string | null;
  isAdmin: boolean;
  setTokens: (accessToken: string, userId: string, isAdmin: boolean) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  userId: null,
  isAdmin: false,
  setTokens: (accessToken, userId, isAdmin) => set({ accessToken, userId, isAdmin }),
  logout: () => set({ accessToken: null, userId: null, isAdmin: false }),
}));
```

### Silent token refresh in apiClient

On any `401` response, attempt one silent refresh before redirecting to login:

```typescript
// src/api/client.ts
let isRefreshing = false;
let refreshPromise: Promise<boolean> | null = null;

async function attemptTokenRefresh(): Promise<boolean> {
  // Deduplicate concurrent refresh attempts
  if (isRefreshing && refreshPromise) return refreshPromise;
  isRefreshing = true;
  refreshPromise = fetch('/api/v1/auth/refresh', { method: 'POST', credentials: 'include' })
    .then(async (res) => {
      if (!res.ok) return false;
      const { data } = await res.json();
      // data.accessToken is the new JWT
      useAuthStore.getState().setTokens(data.accessToken, data.userId, data.isAdmin);
      return true;
    })
    .catch(() => false)
    .finally(() => {
      isRefreshing = false;
      refreshPromise = null;
    });
  return refreshPromise;
}

export async function fetchWithAuth(url: string, options: RequestInit = {}): Promise<Response> {
  const token = useAuthStore.getState().accessToken;
  const response = await fetch(url, {
    ...options,
    credentials: 'include',  // needed for httpOnly refresh token cookie
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  });

  if (response.status === 401) {
    const refreshed = await attemptTokenRefresh();
    if (!refreshed) {
      useAuthStore.getState().logout();
      window.location.href = '/login';
      throw new Error('Session expired');
    }
    // Retry once with the new token
    return fetchWithAuth(url, options);
  }

  return response;
}
```

### Route guards

Wrap protected routes in a guard component that checks auth state:

```tsx
// src/components/domain/RequireAuth.tsx
import { Navigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '../../store/authStore';

export function RequireAuth({ children }: { children: React.ReactNode }) {
  const accessToken = useAuthStore(s => s.accessToken);
  const location = useLocation();

  if (!accessToken) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }
  return <>{children}</>;
}

// src/components/domain/RequireAdmin.tsx
export function RequireAdmin({ children }: { children: React.ReactNode }) {
  const isAdmin = useAuthStore(s => s.isAdmin);
  if (!isAdmin) return <Navigate to="/" replace />;
  return <>{children}</>;
}
```

### Campaign role derivation (D-043)

Campaign role is NOT in the JWT. It comes from the campaign membership API response and is
stored in the campaign Zustand store:

```typescript
// src/store/campaignStore.ts
import { create } from 'zustand';

type CampaignRole = 'gm' | 'editor' | 'player';

interface CampaignState {
  campaignId: string | null;
  currentUserRole: CampaignRole | null;
  setCampaign: (campaignId: string, role: CampaignRole) => void;
  clearCampaign: () => void;
}

export const useCampaignStore = create<CampaignState>((set) => ({
  campaignId: null,
  currentUserRole: null,
  setCampaign: (campaignId, role) => set({ campaignId, currentUserRole: role }),
  clearCampaign: () => set({ campaignId: null, currentUserRole: null }),
}));
```

When entering a campaign context (e.g., navigating to the campaign dashboard), fetch the
campaign membership and call `setCampaign` with the role:

```typescript
// src/hooks/useCurrentCampaignRole.ts
export function useCurrentCampaignRole(): CampaignRole | null {
  return useCampaignStore(s => s.currentUserRole);
}
```

**Never read role from the JWT claims.** The JWT carries only `user_id` and `is_admin`.

---

## Common Pitfalls

- **Storing the access token in `localStorage`.** `localStorage` persists across browser
  sessions and is accessible to any JS on the page (XSS risk). Store in Zustand (in-memory) only.

- **Encoding campaign role in the JWT.** The JWT carries only `user_id` and `is_admin`. If
  campaign role were in the JWT, a removed player would retain their role until the token expired.
  Role changes must take effect immediately.

- **Not deduplicating concurrent 401 refresh attempts.** If multiple requests return 401
  simultaneously, the client must issue only one refresh call. The `isRefreshing` guard above
  handles this.

- **Creating a second admin user.** The partial unique index on `users` will reject the insert.
  The application layer must also validate before attempting the insert — do not rely solely on
  the DB constraint for the user-facing error message.

- **Using the raw refresh token as the stored value.** Only the SHA-256 hash is stored in
  `refresh_tokens.token_hash`. The raw 256-bit token is returned to the client once and never
  persisted server-side (D-059).

- **Family revocation on token reuse.** If a `CONSUMED` refresh token is presented, the entire
  `family_id` must be revoked — not just the presented token. This is the security property that
  makes token reuse detection effective.

## References

- `apps/api/CLAUDE.md` §5 (architecture), §7 AUTH-01, §10 (auth gotchas)
- `apps/web/CLAUDE.md` §5 (auth architecture), §7 (no-localStorage rule)
- `DECISIONS.md` D-025, D-043, D-051, D-059, D-070
- `backend-endpoint` skill (for wiring the login and refresh controllers)
- `database-migration` skill (for `refresh_tokens` and `invitations` table schema)
