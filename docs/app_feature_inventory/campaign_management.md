# Module Name: Campaign Management

## 1. Overview

A campaign is the top-level container of Blue Steel: all sessions, world state, queries, and exploration views are scoped to one campaign. Campaign lifecycle is split between two roles: the platform **Admin** creates and deletes campaigns (assigning exactly one GM atomically at creation — a campaign is never GM-less, D-061), while the campaign's **GM** manages the member roster (invite, change role, remove). Two invariants are enforced at the database level: exactly one GM per campaign, and one membership per (campaign, user). Authorization for every member-management action is resolved from the `campaign_members` table on each request, never from the JWT (D-043).

## 2. Capabilities & Use Cases

- **Use Case / Action:** Admin creates a campaign and assigns its GM — ✅ Implemented
- **Actor:** Admin
- **Functional Description:** Admin provides a campaign name and selects the GM via an email typeahead search. Campaign and the GM membership row are inserted in the same transaction. The GM cannot be changed after creation in v1.
- **Technical Reference / Source Files:** `POST /api/v1/campaigns` — `apps/api/src/main/java/com/bluesteel/adapters/in/web/campaign/CampaignController.java`, `apps/api/src/main/java/com/bluesteel/application/service/campaign/CreateCampaignService.java`, `apps/web/src/features/campaigns/CreateCampaignPage.tsx`

---

- **Use Case / Action:** Admin fixes the campaign's content language at creation — ✅ Implemented (v2, Phase 9, D-099/D-103)
- **Actor:** Admin (at creation; chosen on behalf of the GM)
- **Functional Description:** At creation the campaign is assigned one **content language** (EN/ES, default EN), which is **immutable thereafter** — there is no API path to change it (an invalid value returns `400`; pre-existing campaigns read back as `en`). This single language drives all LLM output for the campaign — narrative extraction, conflict-detection descriptions, and query answers are all produced in it — keeping the stored world state single-language. It is the per-*campaign* language axis, deliberately independent of a user's per-*user* UI locale (D-099). Embeddings are byte-for-byte unchanged: the embedding models are multilingual and consistency is guaranteed by the per-campaign constraint, not by tagging vectors (D-103). The create form offers the EN/ES choice; the campaign home page displays the chosen language read-only with no affordance to edit it.
- **Technical Reference / Source Files:** schema `apps/api/src/main/resources/db/changelog/0030_add_campaign_content_language.xml`; domain `apps/api/src/main/java/com/bluesteel/domain/campaign/Campaign.java` (`contentLanguage`, default `en`); `CreateCampaignRequest.java` (`@Pattern("^(en|es)$")`) → `CampaignController.java`; language injected into prompts via `apps/api/src/main/java/com/bluesteel/adapters/out/ai/PromptLanguage.java` (`SpringAiNarrativeExtractionAdapter.java`, `SpringAiConflictDetectionAdapter.java`, `QueryPromptAssembler.java`); query path reads it from the campaign in `QueryService.java`; frontend `apps/web/src/features/campaigns/CreateCampaignPage.tsx` + read-only display in `CampaignHomePage.tsx` (options in `apps/web/src/i18n/localeOptions.ts`)

---

- **Use Case / Action:** User lists and opens their campaigns — ✅ Implemented
- **Actor:** Authenticated User (any role; Admin sees all campaigns)
- **Functional Description:** The home page (`/`) lists every campaign the caller belongs to, with a role badge (GM/Editor/Player). Selecting a campaign loads its context (active campaign + caller's role) and lands on the campaign home page, which offers navigation into the three modes and, for GMs, member management.
- **Technical Reference / Source Files:** `GET /api/v1/campaigns`, `GET /api/v1/campaigns/{id}` — `CampaignController.java`, `apps/api/src/main/java/com/bluesteel/application/service/campaign/ListCampaignsService.java`, `GetCampaignService.java`, `apps/web/src/features/campaigns/CampaignListPage.tsx`, `CampaignHomePage.tsx`, `apps/web/src/components/domain/CampaignContextGuard.tsx`

---

- **Use Case / Action:** Admin deletes a campaign — ✅ Implemented
- **Actor:** Admin
- **Functional Description:** Irreversible deletion of the campaign and, via database cascade rules, all of its sessions, narrative blocks, world-state entities, versions, embeddings, and annotations. The UI requires confirmation through a focused overlay in the campaign home "danger zone".
- **Technical Reference / Source Files:** `DELETE /api/v1/campaigns/{id}` — `CampaignController.java`, `apps/api/src/main/java/com/bluesteel/application/service/campaign/DeleteCampaignService.java`, cascade migrations `apps/api/src/main/resources/db/changelog/0020_campaign_cascade_delete.xml` et seq., `apps/web/src/features/campaigns/components/DeleteCampaignConfirmOverlay.tsx`

---

- **Use Case / Action:** GM or admin exports a campaign as a portable archive — ✅ Implemented (v2, Phase 7, D-112)
- **Actor:** GM or Admin
- **Functional Description:** Downloads the campaign's full data (metadata, members, the four world-state entity types each with complete version history, annotations, sessions) as a single structured JSON archive, served as a raw file attachment (`Content-Disposition: attachment`) rather than the `{data,meta,errors}` envelope. The pre-deletion guard rail to the delete above: visible to GM **and** admin in the campaign home "danger zone" (delete stays admin-only). Authorization (resolved from `campaign_members` per request, D-043) and a cheap `COUNT` cap run before any rows load; an oversized campaign returns `422 EXPORT_TOO_LARGE` (env-overridable `CAMPAIGN_EXPORT_MAX_ENTITIES`), shown as a specific message in the UI. The archive is streamed (`StreamingResponseBody`) so no full copy is buffered in heap.
- **Technical Reference / Source Files:** `GET /api/v1/campaigns/{id}/export` — `CampaignExportController.java`, `apps/api/src/main/java/com/bluesteel/application/service/campaign/ExportCampaignService.java`, `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/campaign/CampaignExportReadAdapter.java`, `Archived*` records under `application/model/campaign/`; `apps/web/src/features/campaigns/components/CampaignExportButton.tsx`, `apps/web/src/lib/downloadBlob.ts`, `apps/web/src/api/campaigns.ts` (`exportCampaign`/`useExportCampaign`), `apps/web/src/api/client.ts` (`apiClient.download`)

---

- **Use Case / Action:** Member views the campaign roster — ✅ Implemented
- **Actor:** Any campaign member, Admin
- **Functional Description:** Lists all members of the campaign with their roles. Rendered in the member-management panel on the campaign home page.
- **Technical Reference / Source Files:** `GET /api/v1/campaigns/{id}/members` — `apps/api/src/main/java/com/bluesteel/adapters/in/web/campaign/CampaignMembershipController.java`, `apps/api/src/main/java/com/bluesteel/application/service/campaign/ListCampaignMembersService.java`, `apps/web/src/features/campaigns/components/MemberManagementPanel.tsx`

---

- **Use Case / Action:** GM invites a member to the campaign — ✅ Implemented
- **Actor:** GM
- **Functional Description:** GM enters an email and picks a role (Editor or Player — GM is not assignable post-creation). If the email has no platform account yet, one is created with a temporary password and an invitation email is sent (201); an existing user is simply added (200). Inviting someone already in the campaign returns 409.
- **Technical Reference / Source Files:** `POST /api/v1/campaigns/{id}/invitations` — `CampaignMembershipController.java`, `apps/api/src/main/java/com/bluesteel/application/service/campaign/InviteCampaignMemberService.java`, `apps/web/src/features/campaigns/components/MemberManagementPanel.tsx`

---

- **Use Case / Action:** GM changes a member's role — ✅ Implemented
- **Actor:** GM
- **Functional Description:** Switches a member between Editor and Player via a dropdown. The GM role is protected: it cannot be granted or revoked through this endpoint. Because roles are resolved from the DB per request, the change takes effect on the member's very next API call.
- **Technical Reference / Source Files:** `PATCH /api/v1/campaigns/{id}/members/{uid}` — `CampaignMembershipController.java`, `apps/api/src/main/java/com/bluesteel/application/service/campaign/ChangeMemberRoleService.java`

---

- **Use Case / Action:** GM removes a member from the campaign — ✅ Implemented
- **Actor:** GM
- **Functional Description:** Removes an Editor or Player after a confirmation overlay. Removing the GM is rejected (422 `CANNOT_REMOVE_GM`) — a campaign must always have its GM.
- **Technical Reference / Source Files:** `DELETE /api/v1/campaigns/{id}/members/{uid}` — `CampaignMembershipController.java`, `apps/api/src/main/java/com/bluesteel/application/service/campaign/RemoveMemberService.java`, `apps/web/src/features/campaigns/components/RemoveMemberConfirmOverlay.tsx`

---

- **Use Case / Action:** Role-gated navigation inside a campaign — ✅ Implemented
- **Actor:** System (frontend)
- **Functional Description:** The sidebar exposes the three modes (Input / Query / Exploration) according to the caller's role: Players do not see Input Mode and are redirected away from its routes; member management appears only for the GM; campaign deletion only for the Admin. (The former disabled "Settings — Coming soon" sidebar stub was removed in Phase 8; user settings now live on the global `/settings` route reached from the top-right account menu — see [user_management.md](user_management.md).)
- **Technical Reference / Source Files:** `apps/web/src/components/domain/Sidebar.tsx`, `apps/web/src/store/campaignStore.ts`, `apps/web/src/main.tsx` (route guards)

## 3. Core User Journeys (Workflows)

**Journey: Standing up a new campaign**
1. Admin opens `/campaigns/new`, names the campaign, and searches for the future GM by email (the GM must already have a platform account — invite first via `/invite` if not).
2. Backend creates the campaign and the GM membership atomically; the campaign appears in both the Admin's and the GM's campaign lists.
3. GM opens the campaign home and invites the rest of the table from the Members panel: editors (co-writers) and players (read-only).
4. New invitees go through temporary-password onboarding; existing users see the campaign on their home page immediately.

**Journey: GM manages the roster mid-campaign**
1. GM opens the campaign home → Members panel shows the roster with roles.
2. To promote a player who now helps with session notes: change their role dropdown from Player to Editor — effective on their next request.
3. To remove a departed player: click Remove → confirm in the overlay → the member loses access to the campaign instantly.
4. All outcomes are reported through inline banners (the app uses no toasts by design).
