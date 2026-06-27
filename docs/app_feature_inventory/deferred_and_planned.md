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

- **Use Case / Action:** Manually add a missed entity during diff review (`add` action) — ✅ Implemented (v2, Phase 6)
- **Actor:** GM, Editor
- **Functional Description:** Shipped in Phase 6 (F6.1–F6.2). The diff-review screen has an "Add entity" affordance (a `FocusedOverlay` form) for actors/spaces the extraction missed; added entities ride the commit payload's `addedEntities` list and are created as new entities + first versions at commit, with the same session traceability and async embedding path as extracted ones. The former `422 UNSUPPORTED_ACTION` rejection became positive validation (`422 INVALID_ADDED_ENTITY` / `ADDED_ENTITY_NAME_COLLISION`). Events/relations remain out (they need structured links the form can't supply, D-053).
- **Technical Reference / Source Files:** `apps/api/src/main/java/com/bluesteel/application/service/session/CommitPayloadValidator.java`, `CommitService.java`; `apps/web/src/features/input/components/AddEntityForm.tsx`, `AddedEntityCard.tsx`

---

- **Use Case / Action:** Export a campaign as a portable archive — ✅ Implemented (v2, Phase 7, D-112)
- **Actor:** GM or Admin
- **Functional Description:** Shipped in Phase 7 (F7.1–F7.2) — the final v2 phase, completing the v2 feature set. Downloads a campaign's full data (members, actors/spaces/events/relations with complete version history, annotations, sessions) as a single **structured JSON archive**, served as a raw file attachment (`Content-Disposition: attachment`) rather than the `{data,meta,errors}` envelope, gated to **GM or admin** (authz resolved from `campaign_members` per request, D-043). Authorization (404 unknown → 403 non-GM) and a cheap `COUNT` cap precede any row load; an oversized campaign is rejected with `422 EXPORT_TOO_LARGE` (env-overridable `CAMPAIGN_EXPORT_MAX_ENTITIES`, default 2000), surfaced as a specific message in the UI. The archive is streamed (`StreamingResponseBody`) so no full copy is buffered in heap. Primary purpose is a guard rail before campaign deletion. PRD §7 "public sharing / sharable links" stays post-v2 (D-112).
- **Technical Reference / Source Files:** Backend `ExportCampaignService` / `ExportCampaignUseCase`, `CampaignExportController` (raw-JSON attachment, GM/admin authz, size cap), `CampaignExportReadAdapter` + `CampaignExportReadPort`, `Archived*` model records; frontend `exportCampaign` / `useExportCampaign` (`api/campaigns.ts`), `apiClient.download` (`api/client.ts`), `CampaignExportButton` + `lib/downloadBlob.ts` wired into the `CampaignHomePage` danger zone. See `docs/roadmap/ROADMAP_V2.md` Phase 7.

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

- **Use Case / Action:** Per-campaign content language (multilingual LLM output) — ✅ Implemented (v2, Phase 9)
- **Actor:** GM (chooses at campaign creation)
- **Functional Description:** Shipped in Phase 9 (F9.1–F9.4). Each campaign fixes one **content language** at creation (immutable thereafter); narrative extraction, conflict detection, and query answers are all produced in it, keeping the stored world state single-language. This is the per-*campaign* language axis, independent of a user's UI locale (D-099). Embeddings are unchanged — the embedding models are multilingual and consistency is guaranteed by the per-campaign constraint, not by tagging data. See [campaign_management.md](campaign_management.md) for the capability inventory.
- **Technical Reference / Source Files:** [campaign_management.md](campaign_management.md); schema `apps/api/src/main/resources/db/changelog/0030_add_campaign_content_language.xml`; domain `apps/api/.../domain/campaign/Campaign.java` (`contentLanguage`, default `en`); create API `CreateCampaignRequest.java` (`@Pattern("^(en|es)$")`) → `CampaignController.java`. Language injected into the hardcoded prompts via `apps/api/.../adapters/out/ai/PromptLanguage.java` in `SpringAiNarrativeExtractionAdapter.java`, `SpringAiConflictDetectionAdapter.java`, and `QueryPromptAssembler.java`. Frontend picker `apps/web/src/features/campaigns/CreateCampaignPage.tsx` + read-only display on `CampaignHomePage.tsx` (options in `apps/web/src/i18n/localeOptions.ts`).

---

- **Use Case / Action:** Self-registration and self-service password reset — ❌ Excluded by design (D-051, D-070)
- **Actor:** Anonymous User
- **Functional Description:** The platform is invitation-only; account recovery is handled by re-invitation (which refreshes the temporary password). These are deliberate product exclusions, not gaps.
- **Technical Reference / Source Files:** Re-invitation paths in `InvitePlatformUserService.java` / `InviteCampaignMemberService.java`

---

- **Use Case / Action:** Other explicit v1 exclusions — ❌ Out of scope
- **Actor:** —
- **Functional Description:** Also intentionally absent from v1 (do not implement): E2E test suite (D-056), staging environment (D-044), Spring AI `VectorStore` (D-062 — native pgvector SQL is used instead), real-time collaborative editing (one-active-draft polling model suffices, D-054), audio/image ingestion (text summaries only), public sharing / sharable links (the narrower pre-deletion campaign export shipped in Phase 7, D-112), and a native mobile app (responsive web only).
- **Technical Reference / Source Files:** `CLAUDE.md` §8, `docs/DECISIONS.md`, `docs/PRD.md` §7

## 3. Core User Journeys (Workflows)

**Journey: Player-driven canon correction** — ✅ shipped in Phase 5; see [proposals.md](proposals.md).
1. A player spots an error on an actor/space profile and clicks "Propose a change", recording a field delta.
2. Other players co-sign; the GM approves (optionally editing) or vetoes; approved deltas become a new entity version; unanswered proposals expire per TTL.
3. Annotations remain available as a lightweight, non-canonical alternative to a formal proposal.
