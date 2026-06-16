# Module Name: Session Ingestion (Input Mode)

## 1. Overview

This is the heart of Blue Steel. A GM or Editor pastes the raw, unstructured summary of a play session; an asynchronous LLM pipeline turns it into structured world knowledge — actors, spaces, events, and relations — and presents the result as a reviewable **diff** against the existing world state. Nothing is written to the world automatically: the human reviews every extracted card, resolves ambiguous entity matches, acknowledges detected narrative contradictions, and only then commits (D-002 — user confirmation is the trust boundary). On commit, world state is versioned append-only and vector embeddings are generated in the background for later semantic search.

A campaign can have **at most one active draft** at a time (D-054): submitting while another session is `processing` or `draft` is rejected, and the UI offers to resume the existing draft instead.

Session lifecycle: `PENDING → PROCESSING → DRAFT → COMMITTED` (or `FAILED` / `DISCARDED`).

## 2. Capabilities & Use Cases

- **Use Case / Action:** Submit a session summary — ✅ Implemented
- **Actor:** GM, Editor
- **Functional Description:** The user pastes free-form session notes into a textarea. The backend validates campaign membership and role, enforces the one-active-draft rule (409 `ACTIVE_SESSION_EXISTS`), and checks the summary against a token budget (default 8000 tokens; 422 `SUMMARY_TOO_LARGE` with the limit shown in the UI). On success it persists the session (status `PENDING`) plus an immutable narrative block, fires the async pipeline, and returns 202 immediately. Players attempting submission get 403.
- **Technical Reference / Source Files:** `POST /api/v1/campaigns/{id}/sessions` — `apps/api/src/main/java/com/bluesteel/adapters/in/web/session/SessionController.java`, `apps/api/src/main/java/com/bluesteel/application/service/session/SubmitSessionService.java`, `apps/web/src/features/input/SubmitSessionPage.tsx`

---

- **Use Case / Action:** Run the 4-stage extraction pipeline — ✅ Implemented
- **Actor:** System (async event listener)
- **Functional Description:** A post-commit `SessionSubmittedEvent` triggers the pipeline on a dedicated executor, running four sequential stages. Any stage failure marks the session `FAILED` with a machine-readable reason that the UI surfaces.
  1. **Extraction** — one LLM call turns the narrative into candidate actors, spaces, events, relations, and entity mentions; the session moves to `PROCESSING`.
  2. **Entity resolution** — two-stage and cost-aware (D-041): each mention is embedded and compared to existing entities via pgvector similarity; mentions below the similarity floor (0.75) are classified NEW with no LLM call, while close candidates go to a second LLM call that returns MATCH / NEW / UNCERTAIN.
  3. **Conflict detection** — retrieves bounded world-state context via pgvector and asks the LLM for contradictions between the new narrative and established facts; skipped entirely when no MATCH-resolved entities exist (D-033/D-034). Warnings are non-blocking.
  4. **Diff generation** — synthesizes everything into a structured diff payload (NEW / EXISTING / UNCERTAIN / CONFLICT cards) stored on the session, which moves to `DRAFT`.
- **Technical Reference / Source Files:** `apps/api/src/main/java/com/bluesteel/application/service/session/SessionIngestionEventListener.java`, `ExtractionPipelineService.java`, `EntityResolutionService.java`, `ConflictDetectionService.java`, `DiffGenerationService.java`; LLM adapters in `apps/api/src/main/java/com/bluesteel/adapters/out/ai/`

---

- **Use Case / Action:** Poll processing status — ✅ Implemented
- **Actor:** System (frontend polling), on behalf of GM/Editor
- **Functional Description:** After submission the UI polls a lightweight status endpoint with backoff, showing a skeleton (with accessible live-region updates) while `PROCESSING`. On `DRAFT` it auto-navigates to the diff review screen; on `FAILED` it shows the failure reason in an error banner.
- **Technical Reference / Source Files:** `GET /api/v1/campaigns/{id}/sessions/{sid}/status` — `SessionController.java`, `apps/api/src/main/java/com/bluesteel/application/service/session/GetSessionStatusService.java`, `apps/web/src/features/input/ProcessingStatusView.tsx`

---

- **Use Case / Action:** Recover sessions stuck in processing — ✅ Implemented
- **Actor:** System (scheduled job)
- **Functional Description:** A scheduler periodically (every 5 minutes by default) scans for sessions stuck in `PROCESSING` beyond the timeout threshold (10 minutes by default) and marks them `FAILED` with reason `PROCESSING_TIMEOUT`, unblocking the campaign's one-active-draft slot.
- **Technical Reference / Source Files:** `apps/api/src/main/java/com/bluesteel/application/service/session/SessionTimeoutRecoveryScheduler.java`

---

- **Use Case / Action:** Review the structured diff — ✅ Implemented
- **Actor:** GM, Editor
- **Functional Description:** The diff review screen presents an LLM-generated narrative summary header plus four card categories:
  - **NEW entity cards** — full proposed profile for first appearances (D-007); actions: accept / edit / delete.
  - **EXISTING (delta) cards** — only the changed fields against the matched entity (D-006); actions: accept / edit / delete.
  - **UNCERTAIN cards** — the AI could not decide whether a mention matches an existing entity; the reviewer must choose MATCH (link to a candidate) or NEW. Resolution is mandatory — commit is blocked until every UNCERTAIN card is resolved (D-042).
  - **CONFLICT cards** — detected contradictions with established world state; non-blocking, but each must be explicitly acknowledged before commit (D-033). The reviewer keeps authority to accept a retcon.
  Field edits happen in a focused overlay; closing without saving reverts the card to "accept".
- **Technical Reference / Source Files:** `GET /api/v1/campaigns/{id}/sessions/{sid}/diff` — `SessionController.java`, `apps/api/src/main/java/com/bluesteel/application/service/session/GetSessionDiffService.java`; UI: `apps/web/src/features/input/DiffReviewPage.tsx`, `components/NewEntityCard.tsx`, `DeltaCard.tsx`, `UncertainCard.tsx`, `ConflictWarningCard.tsx`, `EditCardOverlay.tsx`, `NarrativeSummaryHeader.tsx`, `hooks/useDiffState.ts`

---

- **Use Case / Action:** Commit the reviewed diff to world state — ✅ Implemented
- **Actor:** GM, Editor
- **Functional Description:** Sends every card decision, every UNCERTAIN resolution, the conflict acknowledgments, and any reviewer-added entities (`addedEntities`). The backend re-validates the payload as defence in depth (unknown/duplicate card IDs, incomplete decisions, unresolved UNCERTAIN cards → 422 `UNCERTAIN_ENTITIES_PRESENT`, unacknowledged conflicts, cross-campaign entity references, and added-entity integrity → 422 `INVALID_ADDED_ENTITY` / `ADDED_ENTITY_NAME_COLLISION`). On success it writes the entities and their new versions, assigns the session's sequence number, transitions it to `COMMITTED`, clears the stored diff, and returns 200 immediately — embeddings follow asynchronously. The UI commit button is disabled until the diff is fully resolved.
- **Technical Reference / Source Files:** `POST /api/v1/campaigns/{id}/sessions/{sid}/commit` — `SessionController.java`, `apps/api/src/main/java/com/bluesteel/application/service/session/CommitService.java`, `CommitPayloadValidator.java`, `apps/web/src/features/input/components/CommitButton.tsx`, `hooks/useCommitPayload.ts`

---

- **Use Case / Action:** Generate embeddings for committed entities — ✅ Implemented
- **Actor:** System (async event listener)
- **Functional Description:** A `SessionCommittedEvent` triggers background embedding of every committed entity version into the pgvector `entity_embeddings` table (D-063). Failures are logged per entity and do not abort the batch or affect the already-returned commit response. These embeddings power entity resolution and Query Mode retrieval.
- **Technical Reference / Source Files:** `apps/api/src/main/java/com/bluesteel/application/service/session/EmbeddingGenerationListener.java`, `apps/api/src/main/java/com/bluesteel/adapters/out/persistence/embedding/EntityEmbeddingWriteAdapter.java`

---

- **Use Case / Action:** Discard a draft — ✅ Implemented
- **Actor:** GM (only — Editors cannot discard)
- **Functional Description:** Abandons a `DRAFT` session after a confirmation overlay: status becomes `DISCARDED`, the diff payload is cleared, and the campaign's active-draft slot is freed. Available from the diff review screen and the sessions list.
- **Technical Reference / Source Files:** `DELETE /api/v1/campaigns/{id}/sessions/{sid}` — `SessionController.java`, `apps/api/src/main/java/com/bluesteel/application/service/session/DiscardSessionService.java`, `apps/web/src/features/input/components/DiscardConfirmOverlay.tsx`

---

- **Use Case / Action:** Browse session history and detail — ✅ Implemented
- **Actor:** Any campaign member
- **Functional Description:** A paginated list of all sessions with status badges and dates; `DRAFT` rows offer Resume (to diff review) and Discard. The read-only session detail page shows sequence number, status, commit date, and the original raw narrative — it is also the landing target of Query Mode citations.
- **Technical Reference / Source Files:** `GET /api/v1/campaigns/{id}/sessions`, `GET .../sessions/{sid}` — `SessionController.java`, `ListSessionsService.java`, `GetSessionDetailService.java`, `apps/web/src/features/input/SessionsListPage.tsx`, `SessionDetailPage.tsx`

---

- **Use Case / Action:** Manually add an entity the AI missed — ✅ Implemented (v2, F6.1/F6.2, D-053)
- **Actor:** GM, Editor
- **Functional Description:** From the diff review screen, a reviewer opens an "Add entity" focused overlay (D-082) and supplies a type, a name, and free-form key/value profile fields for an entity the extraction missed. Added entities appear as their own card category with a remove affordance and ride the commit payload via the dedicated `addedEntities` list (separate from card decisions, so they never collide with card-id validation). On commit they are written as brand-new heads + first versions, stamped with the committing session and embedded asynchronously, exactly like extracted entities. Scope is limited to **actor** and **space**: events and relations depend on structured links (endpoints, involved actors, event type) that the generic form cannot supply, so the backend rejects them with 422 `INVALID_ADDED_ENTITY`. To protect world-state integrity (manual-add bypasses entity resolution), an added name that collides with a same-type diff card or an already-committed entity is rejected with 422 `ADDED_ENTITY_NAME_COLLISION`.
- **Technical Reference / Source Files:** `CommitPayloadValidator.java`, `CommitService.java` (write path), `apps/web/src/features/input/components/AddEntityForm.tsx`, `AddedEntityCard.tsx`, `hooks/useDiffState.ts`, `hooks/useCommitPayload.ts`

## 3. Core User Journeys (Workflows)

**Journey: From play session to committed world state (the core loop)**
1. GM/Editor opens Input Mode (`/campaigns/:id/sessions/new`) and pastes the session notes. If a draft already exists, a persistent warning offers a "Resume" link instead.
2. Submit → 202; the UI polls status while the pipeline runs extraction → resolution → conflict detection → diff generation.
3. On `DRAFT`, the UI auto-navigates to `/sessions/:sid/diff`.
4. Reviewer works through the cards: accepts or edits new/changed entities, deletes false positives, resolves every UNCERTAIN match (MATCH vs NEW), and ticks each conflict acknowledgment.
5. The Commit button enables only when nothing is left unresolved; on commit, the world state gains new entity versions, the session gets its sequence number, and the user is returned to the campaign home.
6. In the background, embeddings are generated so the new knowledge is immediately reachable by future entity resolution and Query Mode.

**Journey: A failed or stuck ingestion**
1. The pipeline fails (LLM error) or hangs; in the hang case the timeout scheduler marks the session `FAILED` within the timeout window.
2. The polling UI surfaces the failure reason in an error banner; the active-draft slot is released.
3. The user fixes the input if needed (e.g., summary too large) and resubmits.
