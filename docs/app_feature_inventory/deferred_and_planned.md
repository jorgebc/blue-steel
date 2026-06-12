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

- **Use Case / Action:** Campaign/user settings screen — 🚧 In Progress / Planned
- **Actor:** Authenticated User
- **Functional Description:** The sidebar shows a disabled "Settings — Coming soon" item; no route or backend exists yet.
- **Technical Reference / Source Files:** `apps/web/src/components/domain/Sidebar.tsx` (ComingSoonItem)

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
