# Module Name: Proposals & Approval Pipeline

## 1. Overview

The Proposal & Approval Pipeline gives campaign members a structured way to correct or extend world
state **without write access** (D-016). A member proposes a field-level change to an existing actor
or space, another member co-signs it, and the GM approves (optionally editing the delta first) or
vetoes. An approved proposal is applied as a **new entity version** through the normal world-state
write path, so the canonical history stays append-only and queryable. Proposals that nobody acts on
expire automatically.

This activates the workflow that v1 shipped as data-model-only: the `proposals` / `proposal_votes`
tables (migrations `0018`/`0019`) and the previously disabled "Propose a change" button (D-012).
v2 scope is **actor and space targets only** — event/relation and brand-new-entity proposals are
deferred (D-108).

Two session references travel with a proposal and must not be confused: the **provenance** session
the author selects at creation (context only, `proposals.session_id`) and the **latest committed**
session that the approved version is actually stamped with, which keeps `version_number` ↔ session
order monotonic (D-107).

## 2. Capabilities & Use Cases

- **Use Case / Action:** Propose a change to an actor or space — ✅ Implemented
- **Actor:** Any campaign member (GM, Editor, Player)
- **Functional Description:** From an actor/space profile the member opens the "Propose a change"
  overlay, edits the entity's current primitive fields, and picks a provenance session. The captured
  flat field delta (mirroring entity-version `changed_fields`, D-104) is submitted. The backend
  validates membership, the actor/space target scope (422 `UNSUPPORTED_TARGET_TYPE` otherwise),
  target existence (404 `PROPOSAL_TARGET_NOT_FOUND`), the provenance session (404 `SESSION_NOT_FOUND`),
  a non-empty delta (422 `EMPTY_DELTA`), and the concurrent-proposal rule (409
  `CONCURRENT_PROPOSAL_EXISTS`), then stamps `expires_at` from the configured TTL and persists an
  `open` proposal.
- **Technical Reference / Source Files:** `POST /api/v1/campaigns/{id}/proposals` —
  `apps/api/.../adapters/in/web/proposal/ProposalController.java`,
  `apps/api/.../application/service/proposal/ProposalCreationService.java`; UI:
  `apps/web/src/components/domain/ProposeChangeButton.tsx`, `ProposalSubmitForm.tsx`,
  `apps/web/src/api/proposals.ts`

---

- **Use Case / Action:** Browse an entity's proposal thread and co-sign — ✅ Implemented
- **Actor:** Any campaign member (co-sign limited to non-authors)
- **Functional Description:** Each actor/space profile shows a thread of that entity's proposals with
  a status badge for the five states (`open`, `cosigned`, `approved`, `rejected`, `expired`). The
  thread is scoped to the entity server-side (target filter on the list endpoint), so no proposal is
  missed beyond the first page. Any member other than the author may co-sign an open proposal once;
  the first co-sign transitions it `open → cosigned` and surfaces it to the GM (D-017). The author
  cannot co-sign (422 `AUTHOR_CANNOT_COSIGN`) and a repeat vote is rejected (409 `DUPLICATE_VOTE`,
  backed by `uidx_proposal_votes_proposal_voter`).
- **Technical Reference / Source Files:** `POST .../proposals/{pid}/votes`,
  `GET .../proposals?targetType=&targetId=` — `ProposalController.java`,
  `apps/api/.../application/service/proposal/ProposalCoSignService.java`,
  `ListProposalsService.java`; UI: `apps/web/src/components/domain/ProposalThread.tsx`,
  `ProposalStatusBadge.tsx`

---

- **Use Case / Action:** GM review queue — approve (with optional edit) or veto — ✅ Implemented
- **Actor:** GM only
- **Functional Description:** A GM-gated campaign queue lists `cosigned` proposals. Approving writes
  the effective delta (the GM-edited delta if supplied, else the author's, D-110) as a new entity
  version stamped with the latest committed session (D-107), records an `approve` vote, stores the
  `resulting_entity_version_id`, and publishes `SessionCommittedEvent` so the new version is embedded
  for Query Mode (D-063). Vetoing records a `reject` vote and flips the status with no world-state
  write. The decision is GM-only (403 `FORBIDDEN`) and requires a `cosigned` proposal (409). Approval
  feedback links to the resulting entity version.
- **Technical Reference / Source Files:** `POST .../proposals/{pid}/decision` —
  `ProposalController.java`, `apps/api/.../application/service/proposal/ProposalDecisionService.java`,
  `ProposalDeltaMapper.java`; UI:
  `apps/web/src/features/proposals/ProposalReviewQueuePage.tsx`, `ProposalReviewCard.tsx`,
  GM-gated nav in `apps/web/src/components/domain/Sidebar.tsx`

---

- **Use Case / Action:** Automatic TTL expiry of stale proposals — ✅ Implemented
- **Actor:** System
- **Functional Description:** A scheduled sweep flips `open`/`cosigned` proposals past their
  `expires_at` to `expired` in one bulk statement, keeping the GM queue clean (D-019, D-105). The TTL
  (default 30 days) and sweep interval are env-overridable; terminal proposals are never touched.
- **Technical Reference / Source Files:**
  `apps/api/.../application/service/proposal/ProposalExpiryScheduler.java`,
  `apps/api/.../adapters/out/persistence/proposal/ProposalExpiryAdapter.java`

---

- **Use Case / Action:** One open proposal per target (concurrent-proposal rule) — ✅ Implemented
- **Actor:** System
- **Functional Description:** At most one `open`/`cosigned` proposal may target a given entity at a
  time (D-106). Enforced by an application pre-check **and** the partial unique index
  `uidx_proposals_open_target`, which closes the check-then-insert race; either path surfaces 409
  `CONCURRENT_PROPOSAL_EXISTS`.
- **Technical Reference / Source Files:** `ProposalCreationService.java`,
  `apps/api/src/main/resources/db/changelog/0027_proposal_indexes.xml`

---

- **Use Case / Action:** Event/relation and new-entity proposals — 🚧 Deferred (post-v2, D-108)
- **Actor:** —
- **Functional Description:** v2 scope is actor/space edits only. Proposing changes to events or
  relations, or creating brand-new entities via a proposal, is out of scope; creation rejects other
  target types with 422.
- **Technical Reference / Source Files:** N/A (rejected in `ProposalCreationService.java`)

## 3. Core User Journeys (Workflows)

**Journey: Player-driven canon correction**
1. A player spots an error on an actor profile and clicks "Propose a change", edits the field(s),
   selects the related session, and submits — the proposal appears in the thread as `open`.
2. Another player co-signs it → status `cosigned`; it now shows in the GM's review queue.
3. The GM reviews the delta, optionally edits it, and approves → a new entity version is written
   (visible on the profile and to Query Mode once embedded) and the proposal links to it; or the GM
   vetoes → status `rejected`, world state unchanged.
4. If nobody acts within the TTL, the scheduler expires the proposal and the queue stays clean.
