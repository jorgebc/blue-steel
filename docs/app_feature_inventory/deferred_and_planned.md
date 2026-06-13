# Module Name: Deferred & Planned Features (v2 Register)

## 1. Overview

This register consolidates everything that exists in the repository only as schema, disabled UI stubs, or recorded decisions — so the inventory cannot be misread as claiming these capabilities work today. Each entry cites the decision ID from `docs/DECISIONS.md` and the artifacts that already exist. Per the repo's standing instruction (`CLAUDE.md` §8), agents must **not** implement these in v1.

## 2. Capabilities & Use Cases

- **Use Case / Action:** Player proposes a change to world state, co-signed by players and approved/rejected by the GM — 🚧 Planned (v2, D-016/D-017)
- **Actor:** Player (propose/co-sign), GM (approve/reject)
- **Functional Description:** The complete approval workflow — proposal creation, voting, expiry (D-019), and application of approved deltas — is deferred to v2. What ships in v1: the database schema (`proposals`, `proposal_votes`) and a disabled "Propose a change" button on every exploration profile with a "Coming in a future update" tooltip (D-012).
- **Technical Reference / Source Files:** Schema: `apps/api/src/main/resources/db/changelog/0018_create_proposals.xml`, `0019_create_proposal_votes.xml`; UI stub: `apps/web/src/components/domain/ProposeChangeButton.tsx`

---

- **Use Case / Action:** Browse the campaign's Q&A history — 🚧 Planned (v2, D-058)
- **Actor:** Any campaign member
- **Functional Description:** Query Mode is stateless in v1; questions and answers are not persisted. A browsable Q&A log requires new storage plus a history UI.
- **Technical Reference / Source Files:** None (intentionally no storage)

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

- **Use Case / Action:** User settings & personalization — 🚧 Planned (v2, D-100/D-101/D-102, Phase 8 F8.1–F8.7)
- **Actor:** Authenticated User
- **Functional Description:** A persistent user identity and preferences: a cosmetic **display name** (non-unique; email stays the login identifier) shown in place of the raw email, an **initials + accent-color avatar** (no uploaded images, no object storage), a **UI locale** (EN/ES), and a **theme** (light/dark/system). These are stored as columns on `users` and reached through a new **top-right account menu** (avatar dropdown: name + email, Settings link, inline theme + language toggles, Log out) plus a global `/settings` route — **replacing** the campaign sidebar's disabled "Settings — Coming soon" stub, which is removed. Preferences persist server-side (source of truth) and are mirrored to `localStorage` to avoid a theme/language flash on first paint.
- **Technical Reference / Source Files:** Today: disabled stub in `apps/web/src/components/domain/Sidebar.tsx` (ComingSoonItem) — to be removed; raw email in `apps/web/src/components/domain/AppBar.tsx`. Planned: `users` columns (migration `0026`), `PATCH /api/v1/users/me`, `components/domain/UserMenu.tsx`, `/settings` route.

---

- **Use Case / Action:** UI internationalization (EN/ES locale selection) — 🚧 Planned (v2, D-099/D-101, Phase 8 F8.6)
- **Actor:** Authenticated User
- **Functional Description:** All UI strings are currently hardcoded English with no i18n library. Planned: an `i18next`/`react-i18next` setup with English and Spanish catalogs, driven by the user's UI locale preference (the per-*user* language axis — distinct from a campaign's content language, D-099). Switchable any time from the account menu or Settings page.
- **Technical Reference / Source Files:** None yet (no i18n library); strings live inline across `apps/web/src/components` and feature pages.

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

**Journey (future, v2): Player-driven canon correction**
1. A player spots an error on an entity profile and clicks "Propose a change" (currently disabled).
2. The proposal records a field delta; other players co-sign; the GM approves or rejects; approved deltas become a new entity version; unanswered proposals expire per TTL.
3. v1 interim path: the player leaves an **annotation** on the entity (works today) and the GM corrects the record in the next session summary via Input Mode.
