---
name: security-hardening
description: >
  Use this skill when performing or extending security hardening on the backend.
  Triggers include: "security headers", "HSTS", "CORS", "rate limiting", "BCrypt DoS",
  "method security", "PreAuthorize", "JWT algorithm", "HTTP security", "password policy",
  "sensitive data in logs", or any task whose goal is reducing the attack surface of the
  Spring Boot API without changing business logic.
---

# Security Hardening — Blue Steel Backend

This skill captures the security decisions made during the first security audit of the
`apps/api` Spring Boot application. Apply these patterns whenever adding new endpoints,
auth flows, or modifying the security configuration.

---

## 1. HTTP Security Headers (`SecurityConfig`)

All security headers are configured in `SecurityConfig` via the `headers()` DSL. The current
set applied to every response:

| Header | Value | Why |
|---|---|---|
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` | Enforces HTTPS for 1 year |
| `X-Content-Type-Options` | `nosniff` | Prevents MIME sniffing |
| `X-Frame-Options` | `DENY` | Clickjacking protection |
| `X-XSS-Protection` | `0` (disabled) | Legacy header; disabled to avoid exploitation |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | Minimal referrer leakage |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=()` | Restrict browser APIs |

These are configured in `SecurityConfig.securityFilterChain()`:

```java
.headers(headers -> headers
    .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
    .contentTypeOptions(ct -> {})
    .frameOptions(frame -> frame.deny())
    .xssProtection(xss -> xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.DISABLED))
    .referrerPolicy(ref -> ref.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
    .permissionsPolicy(pp -> pp.policy("camera=(), microphone=(), geolocation=()")))
```

**CSP is not set here** — the API serves only JSON (no HTML). If an HTML endpoint is ever added,
add a `Content-Security-Policy` header in `SecurityConfig`.

---

## 2. CORS (`WebConfig`)

CORS is configured in `WebConfig.addCorsMappings()`. Key invariants:

- `http://localhost:5173` is only added to the allowlist when the `local` Spring profile is
  active. **Never expose the local dev origin in production.**
- Production frontend origin comes from `VITE_API_BASE_URL` environment variable.
- `allowedHeaders` is an explicit list, not `"*"`: `Authorization`, `Content-Type`, `Accept`,
  `Origin`, `X-Requested-With`.
- `allowCredentials(true)` is required for the `httpOnly` refresh token cookie to be sent.

```java
// WebConfig.java — detecting active profile
@Value("${spring.profiles.active:}")
private String activeProfiles;

if (activeProfiles.contains("local")) {
    origins.add("http://localhost:5173");
}
```

> **Caveat:** `spring.profiles.active` may not be set if profiles are activated via other
> mechanisms (e.g., `spring.profiles.include`). If the CORS allowlist is not behaving as
> expected, verify with `/api/v1/health` that the profile is active.

---

## 3. Method Security and Admin Role Enforcement

`@EnableMethodSecurity` is present on `SecurityConfig`. Use `@PreAuthorize` for endpoint-level
role enforcement on controllers. The use-case service provides a second layer of enforcement
(defence in depth).

```java
// Controller — first enforcement layer
@PostMapping
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<...> adminOnlyEndpoint(...) { ... }

// Service — second enforcement layer (domain exception, not Spring Security)
if (!command.callerIsAdmin()) {
    throw new UnauthorizedException("Only admins may perform this action");
}
```

**Never check roles by manually inspecting `Authentication.getAuthorities()`** in controllers.
Use `@PreAuthorize`. The authorities are set by `JwtAuthenticationFilter` from the JWT claims
and are: `ROLE_ADMIN` + `ROLE_USER` (for admin users) or `ROLE_USER` only (for regular users).

---

## 4. JWT Security (`JwtTokenService` and `JwtAuthenticationFilter`)

Both the filter and the token service use `JwtPort.validate()` — there is **one** validation
code path:

- `JwtAuthenticationFilter` delegates to `JwtPort` (driven port) — it does not re-implement
  validation inline.
- `JwtTokenService.validate()` explicitly checks the algorithm (`JWSAlgorithm.HS256`) before
  calling `verify()` to prevent algorithm-confusion attacks (including `alg: none`).
- The secret minimum length (32 bytes) is enforced in `JwtTokenService.init()` at startup.

If you add a new JWT validation path, always go through `JwtPort.validate()`.

---

## 5. BCrypt DoS Protection

BCrypt is CPU-intensive; very long inputs are a denial-of-service vector. Every request body
that feeds a password field to `PasswordEncoder.matches()` or `PasswordEncoder.encode()` must
have a `@Size(max = 128)` constraint:

| Request | Field | Constraint |
|---|---|---|
| `LoginRequest` | `password` | `@NotBlank @Size(max = 128)` |
| `ChangePasswordRequest` | `currentPassword` | `@NotBlank` |
| `ChangePasswordRequest` | `newPassword` | `@NotBlank @Size(min = 12, max = 128)` |

---

## 6. Password Policy

New password minimum: **12 characters** (NIST SP 800-63B). Maximum: **128 characters** (BCrypt
DoS protection). No complexity requirements — length is the primary security factor.

---

## 7. Sensitive Data in Logs

Never log:
- Passwords or password hashes (any form)
- JWT tokens (access or refresh)
- Temporary passwords sent via email
- Any `EmailMessage.body()` that may contain credentials

The `MockEmailAdapter` logs `to` and `subject` only — never `body`.

---

## 8. Missing: Rate Limiting

**Rate limiting on auth endpoints is not implemented in application code.** This is the highest
remaining risk. Mitigations must be applied at the infrastructure layer:

- Configure rate limiting on the reverse proxy / load balancer in front of the API
  (e.g., nginx `limit_req_zone` or Oracle Cloud load balancer policy)
- Apply IP-based rate limiting to `POST /api/v1/auth/login` and `POST /api/v1/auth/refresh`
- Recommended: ≤10 requests/minute per IP to `/api/v1/auth/login`

If application-layer rate limiting is required, add `bucket4j-spring-boot-starter` as a
dependency and configure a `RateLimitingFilter` in `SecurityConfig`.

---

## References

- `OWASP Top 10` — categories A01–A07
- NIST SP 800-63B — Digital Identity Guidelines (password policy)
- `apps/api/CLAUDE.md` §5 (auth), §6 (LOG-02 — never log credentials)
- `DECISIONS.md` D-059 (JWT/refresh token), D-050 (secrets), D-060 (auth implementation)
- `auth` skill — JWT issuance, refresh rotation, filter chain wiring
