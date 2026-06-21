# Module Name: Deferred & Planned Features (v2 Register)

## 1. Overview

This register consolidates everything that exists in the repository only as schema, disabled UI stubs, or recorded decisions — so the inventory cannot be misread as claiming these capabilities work today. Each entry cites the decision ID from `docs/DECISIONS.md` and the artifacts that already exist. Per the repo's standing instruction (`CLAUDE.md` §8), agents must **not** implement these in v1.

## 2. Capabilities & Use Cases

- **Use Case / Action:** Player proposes a change to world state, co-signed by players and approved/rejected by the GM — ✅ Implemented (v2, Phase 5)
- **Actor:** Player (propose/co-sign), GM (approve/reject)
- **Functional Description:** Shipped in Phase 5. The full approval workflow — proposal creation, co-sign voting, GM approve-with-edit/veto, application of approved deltas as new entity versions, the concurrent-proposal rule, and TTL expiry — is now live for **actor/space** targets (event/relation deferred, D-108). See [proposals.md](proposals.md) for the capability inventory.
- **Technical Reference / Source Files:** [proposals.md](proposals.md); schema `apps/api/src/main/resources/db/changelog/0018_create_proposals.xml`, `0019_create_proposal_votes.xml`, `0026_add_proposal_session_and_result.xml`, `0027_proposal_indexes.xml`

---

- **Use Case / Action:** Browse the campaign's Q&A history — ✅ Implemented (v2, Phase 6)
- **Actor:** Any campaign member
- **Functional Description:** Shipped in Phase 6 (F6.3–F6.5). Every successful query is persisted to a campaign-scoped, append-only Q&A log and surfaced through a history panel inside Query Mode (not a 5th Exploration view, per D-058). The live-answer flow remains component-local; the log is read-only with an env-overridable per-campaign retention bound. See [query_mode.md](query_mode.md) for the capability inventory.
- **Technical Reference / Source Files:** [query_mode.md](query_mode.md); schema `apps/api/src/main/resources/db/changelog/0028_create_query_log.xml`

---

- **Use Case / Action:** Manually add a missed entity during diff review (`add` action) — 🚧 Planned (v2, D-053)
- **Actor:** GM, Editor
- **Functional Description:** The commit payload supports only accept/edit/delete decisions; `add` is explicitly rejected with 422 `UNSUPPORTED_ACTION`. v1 workaround: resubmit a corrected summary.
- **Technical Reference / Source Files:** Rejection in `apps/api/src/main/java/com/bluesteel/application/service/session/CommitPayloadValidator.java`

---

- **Use Case / Action:** Streamed query answers (SSE) — 🚧 Planned (revisit in v2 only if latency targets require it, D-052)
- **Actor:** Any campaign member
- **Functional Description:** Answers are returned in one synchronous response; no token streaming.
- **Technical Reference / Source Files:** None

---

- **Use Case / Action:** User settings & personalization — ✅ Implemented (v2, Phase 8)
- **Actor:** Authenticated User
- **Functional Description:** Shipped in Phase 8 (F8.1–F8.7). A persistent user identity and preferences: a cosmetic **display name** (non-unique; email stays the login identifier), an **initials + accent-color avatar** (no uploaded images), a **UI locale** (EN/ES), and a **theme** (light/dark/system) — stored as columns on `users`, exposed on `GET /me`, and edited via `PATCH /me`. Reached through a **top-right account menu** (avatar dropdown: name + email, Settings link, inline theme + language toggles, Log out) plus a global `/settings` route — the campaign sidebar's disabled "Settings — Coming soon" stub was removed. Preferences persist server-side (source of truth) and are mirrored to `localStorage` so the theme is applied on first paint with no flash. See [user_management.md](user_management.md) for the capability inventory.
- **Technical Reference / Source Files:** [user_management.md](user_management.md); schema `apps/api/src/main/resources/db/changelog/0029_add_user_profile_settings.xml`; `PATCH /api/v1/users/me` (`UserController.java`); `apps/web/src/components/domain/UserMenu.tsx`, `InitialsAvatar.tsx`, `apps/web/src/features/settings/UserSettingsPage.tsx`, `apps/web/src/store/settingsStore.ts`, `apps/web/src/hooks/useApplyTheme.ts`

---

- **Use Case / Action:** UI internationalization (EN/ES locale selection) — ✅ Implemented (v2, Phase 8)
- **Actor:** Authenticated User
- **Functional Description:** Shipped in Phase 8 (F8.6). An `i18next`/`react-i18next` runtime with English and Spanish catalogs, driven by the user's UI locale preference (the per-*user* language axis — distinct from a campaign's content language, D-099), switchable any time from the account menu or Settings page and applied without a reload. Phase 8 scope covered the mechanism plus nav-chrome extraction (Sidebar/AppBar/UserMenu); per-page string extraction continues as incremental follow-on work. See [system_platform.md](system_platform.md) for the capability inventory.
- **Technical Reference / Source Files:** [system_platform.md](system_platform.md); `apps/web/src/i18n/index.ts`, `apps/web/src/i18n/locales/en.json`, `es.json`

---

- **Use Case / Action:** Per-campaign content language (multilingual LLM output) — 🚧 Planned (v2, D-099/D-103, Phase 9)
- **Actor:** GM (chooses at campaign creation)
- **Functional Description:** Each campaign fixes one **content language** at creation (immutable thereafter); narrative extraction, conflict detection, and query answers are all produced in it, keeping the stored world state single-language. This is the per-*campaign* language axis, independent of a user's UI locale (D-099). Embeddings are unchanged — the embedding models are multilingual and consistency is guaranteed by the per-campaign constraint, not by tagging data.
- **Technical Reference / Source Files:** Planned: `campaigns.content_language` (migration `0027`), language injected into the hardcoded prompts in `apps/api/.../adapters/out/ai/SpringAiNarrativeExtractionAdapter.java`, `SpringAiConflictDetectionAdapter.java`, and `QueryPromptAssembler.java`.

---

- **Use Case / Action:** Self-registration and self-service password reset — ❌ Excluded by design (D-051, D-070)
- **Actor:** Anonymous User
- **Functional Description:** The platform is invitation-only; account recovery is handled by re-invitation (which refreshes the temporary password). These are deliberate product exclusions, not gaps.
- **Technical Reference / Source Files:** Re-invitation paths in `InvitePlatformUserService.java` / `InviteCampaignMemberService.java`

---

- **Use Case / Action:** Other explicit v1 exclusions — ❌ Out of scope
- **Actor:** —
- **Functional Description:** Also intentionally absent from v1 (do not implement): E2E test suite (D-056), staging environment (D-044), Spring AI `VectorStore` (D-062 — native pgvector SQL is used instead), real-time collaborative editing (one-active-draft polling model suffices, D-054), audio/image ingestion (text summaries only), public sharing/export, and a native mobile app (responsive web only).
- **Technical Reference / Source Files:** `CLAUDE.md` §8, `docs/DECISIONS.md`, `docs/PRD.md` §7

## 3. Core User Journeys (Workflows)

**Journey: Player-driven canon correction** — ✅ shipped in Phase 5; see [proposals.md](proposals.md).
1. A player spots an error on an actor/space profile and clicks "Propose a change", recording a field delta.
2. Other players co-sign; the GM approves (optionally editing) or vetoes; approved deltas become a new entity version; unanswered proposals expire per TTL.
3. Annotations remain available as a lightweight, non-canonical alternative to a formal proposal.
