# Module Name: User Management

## 1. Overview

User accounts in Blue Steel are created exclusively through invitations — there is no public sign-up (D-051). A single platform Admin (DB-enforced singleton) invites users at the platform level; GMs additionally create accounts implicitly when inviting members to their campaigns (covered in [campaign_management.md](campaign_management.md)). Invited users receive a temporary password by email and must change it on first login. There is no self-service password reset by design: a forgotten password is handled by re-inviting the user, which refreshes their credentials (D-070).

## 2. Capabilities & Use Cases

- **Use Case / Action:** System bootstraps the singleton Admin account on startup — ✅ Implemented
- **Actor:** System (application startup)
- **Functional Description:** On boot, the backend ensures the platform Admin account exists, created from the `ADMIN_EMAIL` / `ADMIN_PASSWORD` environment variables. A partial unique index on `users.is_admin` guarantees there can only ever be one Admin.
- **Technical Reference / Source Files:** `apps/api/src/main/java/com/bluesteel/application/service/user/AdminBootstrapService.java`

---

- **Use Case / Action:** Admin invites a new user to the platform — ✅ Implemented
- **Actor:** Admin
- **Functional Description:** Admin enters an email address; the system creates the account with a generated temporary password and emails it to the user (201 for a new account, 200 when re-inviting an existing one — re-invitation refreshes the temporary password, which doubles as the password-reset mechanism). The invited user has no campaign role until added to a campaign.
- **Technical Reference / Source Files:** `POST /api/v1/invitations` — `apps/api/src/main/java/com/bluesteel/adapters/in/web/invitation/InvitationController.java`, `apps/api/src/main/java/com/bluesteel/application/service/user/InvitePlatformUserService.java`, `apps/api/src/main/java/com/bluesteel/application/service/user/TemporaryPasswordGenerator.java`, `apps/web/src/features/admin/InvitePlatformUserPage.tsx`

---

- **Use Case / Action:** Invitation emails are composed and sent — ✅ Implemented
- **Actor:** System (email service)
- **Functional Description:** Both platform and campaign invitations produce a transactional email containing the temporary password and login instructions. The provider is pluggable: a mock adapter that logs to console (default, local development) or Brevo under the `email-real`/`prod` profile.
- **Technical Reference / Source Files:** `apps/api/src/main/java/com/bluesteel/application/service/email/InvitationEmailFactory.java`, `apps/api/src/main/java/com/bluesteel/adapters/out/email/` (`BrevoEmailAdapter`, `MockEmailAdapter`)

---

- **Use Case / Action:** Admin or GM searches existing users by email (typeahead) — ✅ Implemented
- **Actor:** Admin, GM
- **Functional Description:** Partial-email lookup used by the campaign-creation form (Admin picks the GM) and member-invitation flows. Authorization is resolved in the service: the caller must be the Admin or a GM of at least one campaign. The frontend debounces input before querying.
- **Technical Reference / Source Files:** `GET /api/v1/users?email={partial}` — `apps/api/src/main/java/com/bluesteel/adapters/in/web/user/UserSearchController.java`, `apps/api/src/main/java/com/bluesteel/application/service/user/SearchUsersService.java`, `apps/web/src/hooks/useDebouncedValue.ts`

---

- **Use Case / Action:** User views their own profile — ✅ Implemented
- **Actor:** Authenticated User
- **Functional Description:** Returns the caller's id, email, admin flag, and the `forcePasswordChange` flag. The frontend loads it after login to populate the auth store and the top bar (including the "Admin" badge for the Admin).
- **Technical Reference / Source Files:** `GET /api/v1/users/me` — `apps/api/src/main/java/com/bluesteel/adapters/in/web/user/UserController.java`, `apps/api/src/main/java/com/bluesteel/application/service/user/GetCurrentUserService.java`, `apps/web/src/store/authStore.ts`

---

- **Use Case / Action:** User changes their password — ✅ Implemented
- **Actor:** Authenticated User
- **Functional Description:** Requires the current password; the new password must differ from it and meet strength validation (12-char minimum enforced in the UI). On success the `forcePasswordChange` flag is cleared, completing invited-user onboarding.
- **Technical Reference / Source Files:** `PATCH /api/v1/users/me/password` — `UserController.java`, `apps/api/src/main/java/com/bluesteel/application/service/user/ChangePasswordService.java`, `apps/web/src/features/auth/ChangePasswordPage.tsx`

---

- **Use Case / Action:** User resets a forgotten password — 🚧 Planned (deliberately out of scope, D-070)
- **Actor:** Anonymous User
- **Functional Description:** Not available as self-service in v1. The workaround is administrative: the Admin (or a GM, via campaign invitation) re-invites the user, which generates and emails a fresh temporary password and re-enables the forced-change flow.
- **Technical Reference / Source Files:** Covered by the re-invitation path in `InvitePlatformUserService.java` / `InviteCampaignMemberService.java`; no dedicated reset endpoint exists.

## 3. Core User Journeys (Workflows)

**Journey: Admin provisions a new user**
1. Admin opens `/invite` (admin-only route) and enters the user's email.
2. Backend creates the account with a temporary password and `forcePasswordChange = true`; an invitation email is sent.
3. The user follows the onboarding journey described in [authentication.md](authentication.md) (login → forced password change → campaign list).
4. The user remains campaign-less until an Admin assigns them as GM of a new campaign or a GM invites them into an existing campaign.

**Journey: Recovering access for a locked-out user**
1. User reports a forgotten password (no self-service reset exists).
2. Admin re-submits the invitation for that email from `/invite` (response 200 — existing account).
3. The user's credentials are refreshed with a new temporary password, emailed to them; `forcePasswordChange` is set again.
4. User logs in with the temporary password and is forced to choose a new one.
