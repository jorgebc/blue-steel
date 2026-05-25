"""Structural validation test for the Blue Steel planning crew.

Tests WITHOUT making real LLM calls by monkey-patching the agents' run() functions.
Verifies:
  1. All module imports succeed
  2. Agent construction succeeds (model instantiation)
  3. planning_crew.run_planning() writes a plan with all 8 sections
  4. Plan mentions real Blue Steel paths (not placeholders)

Use this for CI verification. Use test_planning.py for full integration testing
with a real model (requires ANTHROPIC_API_KEY or local Ollama running).

Run from repo root:
    python .ai/pipeline/agents/planning/test_planning_structural.py
"""

import os
import re
import sys
from pathlib import Path
from unittest.mock import patch, MagicMock

# Add .ai/pipeline/ to sys.path
sys.path.insert(0, str(Path(__file__).parents[2]))

from tools.filesystem import REPO_ROOT

os.environ.setdefault("PIPELINE_MODE", "local")

_PLAN_SECTIONS = [
    "## 1.",
    "## 2.",
    "## 3.",
    "## 4.",
    "## 5.",
    "## 6.",
    "## 7.",
    "## 8.",
]

_BLUE_STEEL_PATHS = ["apps/api/", "apps/web/", "com.bluesteel.", "db/changelog/"]

_MOCK_PO_OUTPUT_1 = """
## 1. Scope -- In
- main.tsx: React root with QueryClientProvider, BrowserRouter, React Router routes
- Zustand authStore (store/authStore.ts): accessToken in-memory, currentUser
- Zustand campaignStore (store/campaignStore.ts): activeCampaignId, activeRole
- Base HTTP client (api/client.ts): Authorization: Bearer header, 401 silent refresh
- TypeScript types: ApiEnvelope<T>, AuthLoginResponse, UserMeResponse
- features/auth/LoginPage.tsx: React Hook Form + shadcn/ui Form
- features/auth/ChangePasswordPage.tsx: force_password_change guard
- components/domain/RequireAuth.tsx: redirects to /login if no token
- /status page: calls GET /api/v1/health, renders db: UP

## 2. Scope -- Out
- Campaign list page (F1.8)
- Any feature beyond auth (D-051)

## 3. Acceptance Criteria
Scenario 1: Successful login
Given: A user with valid credentials visits /login
When: They submit the form
Then: An access token is stored in authStore and they are redirected to /campaigns

Scenario 2: Force password change redirect
Given: A user logs in with forcePasswordChange = true
When: The login response is received
Then: They are redirected to /change-password before accessing any other route

Scenario 3: Silent token refresh on 401
Given: A user's access token has expired
When: An API call returns 401
Then: The client silently calls POST /auth/refresh and retries the original request

Scenario 4: Player cannot access Input Mode
Given: A user with role = player
When: They navigate to /sessions/new
Then: They are redirected away (role guard) -- applies from F1.8 onward

## 4. User Impact
- GM, Editor, Player: can now log in and change their initial password
- Admin: unchanged (already in F1.5)

## 5. UX Requirements
- Login and change-password forms use InlineBanner for errors (D-083, no toasts)
- No loading spinners -- skeleton or disabled button state (D-086)
- No modal dialogs (D-082)

## 6. Open Questions for the Architect
- Where does Zustand authStore live on the filesystem?
- How is silent refresh implemented in api/client.ts?
"""

_MOCK_ARCH_OUTPUT_1 = """
## 1. Technical Approach Summary
Create the React walking skeleton with full auth UI. The frontend talks to the
Spring Boot backend via a typed HTTP client in apps/web/src/api/client.ts.
Zustand stores hold auth state in-memory. TanStack Query provides server state caching.

## 2. New Files to Create
- apps/web/src/main.tsx
  Layer: frontend root | Responsibility: React root, QueryClientProvider, BrowserRouter
- apps/web/src/api/client.ts
  Layer: frontend api | Responsibility: fetch wrapper with auth header and silent refresh
- apps/web/src/store/authStore.ts
  Layer: frontend store | Responsibility: Zustand auth store (accessToken in-memory, currentUser)
- apps/web/src/store/campaignStore.ts
  Layer: frontend store | Responsibility: Zustand campaign context store
- apps/web/src/types/auth.ts
  Layer: frontend types | Responsibility: ApiEnvelope<T>, AuthLoginResponse, UserMeResponse
- apps/web/src/features/auth/LoginPage.tsx
  Layer: frontend feature | Responsibility: React Hook Form login with shadcn/ui Form
- apps/web/src/features/auth/ChangePasswordPage.tsx
  Layer: frontend feature | Responsibility: Force-password-change flow with route guard
- apps/web/src/components/domain/RequireAuth.tsx
  Layer: frontend component | Responsibility: Route guard -- redirects to /login if no token
- apps/web/src/features/status/StatusPage.tsx
  Layer: frontend feature | Responsibility: GET /api/v1/health round-trip proof
- apps/web/vercel.json
  Layer: deployment config | Responsibility: SPA rewrite rule for React Router

## 3. Existing Files to Modify
None -- this is a new frontend from scratch.

## 4. DB Migration Assessment
Required: No
Reason: All schema changes were completed in F1.4 and F1.5. This is frontend-only.

## 5. API Contracts
POST /api/v1/auth/login -> 200 {data: {accessToken: string, forcePasswordChange: boolean}}
POST /api/v1/auth/refresh -> 200 {data: {accessToken: string}} | 401 REFRESH_TOKEN_REUSE_DETECTED
PATCH /api/v1/users/me/password -> 200 {} | 400 {errors: [{code, message, field}]}
GET /api/v1/health -> 200 {data: {status: UP, db: UP}}

## 6. Architecture Decision Compliance
D-043: JWT carries only user_id + is_admin. Campaign role comes from campaignStore, populated by membership API. [COMPLIANT]
D-059: Access token in-memory (Zustand) never localStorage. Refresh token in httpOnly cookie -- frontend never reads it. [COMPLIANT]
D-077: force_password_change flag drives redirect after login. [COMPLIANT]
D-082: No modals -- errors use InlineBanner. [COMPLIANT]
D-083: No toasts -- system feedback uses InlineBanner. [COMPLIANT]
D-086: No spinners in primary content -- form submit button shows loading state. [COMPLIANT]

## 7. Dependencies on Existing Code
- Spring Boot backend (F1.5, F1.6): POST /auth/login, POST /auth/refresh, PATCH /users/me/password, GET /health
- apps/web/src/components/ui/ (shadcn/ui): Form, Input, Button primitives

## 8. Identified Risks
- Silent refresh race condition: if two concurrent requests both receive 401, only one should
  trigger the refresh. Needs a refresh-in-flight flag in authStore.
- Zustand hydration: accessToken must never survive a page reload (no localStorage).
  Verify that the store is initialized as null on every page load.
"""

_MOCK_PO_CHALLENGE = """
## 1. Scope Compliance
The proposal stays within scope. No scope creep detected.
No forbidden features (D-053 add action, D-058 Q&A log, D-016 proposals) appear.

## 2. Acceptance Criteria Coverage
All 4 scenarios are covered:
- Scenario 1 (successful login): covered by LoginPage.tsx + authStore + client.ts
- Scenario 2 (force password change): covered by ChangePasswordPage.tsx route guard
- Scenario 3 (silent refresh): covered by client.ts refresh-in-flight flag
- Scenario 4 (player role gate): noted as F1.8 concern -- acceptable to defer

## 3. UX Rule Compliance
- D-082 (no modals): confirmed -- InlineBanner used for errors
- D-083 (no toasts): confirmed -- InlineBanner used
- D-086 (no spinners): confirmed -- button loading state
Request: Confirm that InlineBanner is imported from components/domain/ not created inline.

## 4. Role Authorization
F1.7 is auth-only. Role enforcement starts in F1.8. Acceptable.

## 5. Missing Elements
- Add apps/web/src/api/auth.ts as the typed API client file for auth endpoints.
  The client.ts should be the base HTTP client; auth-specific calls belong in auth.ts.
- Confirm vercel.json handles SPA routing for /login and /change-password.

## 6. Specific Requests
1. Add apps/web/src/api/auth.ts to the file list
2. Confirm InlineBanner is from components/domain/InlineBanner.tsx (not shadcn)
3. Confirm silent refresh uses a promise lock (not a race-prone flag)
"""

_MOCK_ARCH_FINAL = """
# Plan: F1.7

## 1. Executive Summary
Task F1.7 delivers the React walking skeleton -- a minimal deployable frontend that
completes the full round-trip: browser -> Spring Boot -> Neon PostgreSQL. It includes
the full authentication UI (login, forced password change) with route guards and the
in-memory JWT storage pattern mandated by D-059.

## 2. Acceptance Criteria (Given/When/Then)
Scenario 1: Successful login
Given: A user with valid credentials visits /login
When: They submit the React Hook Form login form
Then: An access token is stored in Zustand authStore (in-memory, never localStorage)
  And they are redirected to /campaigns (or /change-password if forcePasswordChange=true)

Scenario 2: Force password change redirect
Given: A user logs in and the response includes forcePasswordChange: true
When: The LoginPage.tsx processes the response
Then: React Router redirects to /change-password
  And all other protected routes redirect back to /change-password until password is changed

Scenario 3: Silent token refresh on 401
Given: An access token has expired
When: api/client.ts receives a 401 response
Then: It calls POST /api/v1/auth/refresh (with credentials: include for the httpOnly cookie)
  And retries the original request exactly once
  And redirects to /login if the refresh also returns 401
  And uses a promise lock to prevent concurrent refreshes

Scenario 4: Health check round-trip
Given: The /status page is loaded
When: GET /api/v1/health is called
Then: The response db: UP is rendered, confirming browser -> Spring Boot -> Neon works

## 3. Proposed Technical Solution
Frontend (apps/web/src/):
  - main.tsx: React root, QueryClientProvider, BrowserRouter, React Router v6 routes
  - api/client.ts: fetch wrapper, Authorization: Bearer, 401 silent refresh with promise lock
  - api/auth.ts: typed calls for POST /auth/login, POST /auth/refresh, PATCH /users/me/password
  - store/authStore.ts: Zustand store (accessToken in-memory, currentUser, reset on refresh)
  - store/campaignStore.ts: Zustand store (activeCampaignId, currentUserRole -- empty for now)
  - types/auth.ts: ApiEnvelope<T>, AuthLoginResponse, UserMeResponse
  - features/auth/LoginPage.tsx: React Hook Form + shadcn/ui Form, InlineBanner for errors
  - features/auth/ChangePasswordPage.tsx: React Hook Form, force_password_change route guard
  - components/domain/RequireAuth.tsx: redirects to /login if authStore.accessToken is null
  - components/domain/InlineBanner.tsx: four-variant InlineBanner (success/error/warning/info)
  - features/status/StatusPage.tsx: GET /api/v1/health display
  - vercel.json: SPA rewrite rule (/* -> /index.html)

## 4. Dependencies on Existing Blue Steel Code
Backend (already built in F1.5 and F1.6):
  - POST /api/v1/auth/login
  - POST /api/v1/auth/refresh
  - POST /api/v1/auth/logout
  - PATCH /api/v1/users/me/password
  - GET /api/v1/health
  - GET /api/v1/users/me
shadcn/ui primitives: Form, Input, Button (apps/web/src/components/ui/ -- never edit)

## 5. New or Modified API Contracts
No new API endpoints. Consuming existing contracts from F1.5 and F1.6.
Frontend TypeScript types in apps/web/src/types/auth.ts mirror the backend DTOs exactly.

## 6. DB Migration Required
No. All schema changes are complete (F1.4: auth tables). This task is frontend-only.

## 7. Identified Risks
- Silent refresh race condition: two concurrent expired-token requests could trigger two
  refresh calls. Fixed by a promise lock: if a refresh is in-flight, queue callers.
- Zustand persistence: if developer accidentally adds persist() to authStore, tokens land
  in localStorage. Add a comment warning against this.
- shadcn/ui import path: components/ui/ must NEVER be edited. Wrap in components/domain/.

## 8. Explicitly Out of Scope for This Task
- Campaign list page (F1.8, D-024)
- Campaign-scoped invitation (F1.9, D-064)
- Campaign-level role enforcement at the router (F1.8+ -- role comes from campaign API)
- Input Mode, Query Mode, Exploration Mode (Phase 2-4)
- Player proposal affordance (D-012, ships in v2)
- Q&A log (D-058, deferred to v2)
"""


def _make_mock_llm_response(text: str):
    """Create a mock LiteLLMModel response object."""
    mock = MagicMock()
    mock.content = text
    mock.__str__ = lambda self: text
    return text


def _mock_agent_run(agent_run_fn, return_text: str):
    """Return a mock that bypasses actual LLM calls."""
    return return_text


def main() -> None:
    print("=" * 70)
    print("Blue Steel Planning Crew — Structural Test (no LLM calls)")
    print("=" * 70)

    failures: list[str] = []

    # ── Step 1: Module imports ────────────────────────────────────────────
    print("\n[1/4] Verifying module imports...")
    try:
        import planning_crew
        import po_agent
        import architect_agent
        print("      OK: all modules import successfully")
    except ImportError as e:
        failures.append(f"Import error: {e}")
        print(f"      FAIL: {e}")

    # ── Step 2: Agent construction (model instantiation) ─────────────────
    print("\n[2/4] Verifying agent construction...")
    try:
        agent = po_agent._create_agent()
        print(f"      OK: PO agent created ({type(agent).__name__})")
    except Exception as e:
        failures.append(f"PO agent construction failed: {e}")
        print(f"      FAIL PO agent: {e}")
    try:
        agent = architect_agent._create_agent()
        print(f"      OK: Architect agent created ({type(agent).__name__})")
    except Exception as e:
        failures.append(f"Architect agent construction failed: {e}")
        print(f"      FAIL Architect agent: {e}")

    # ── Step 3: run_planning with mocked agents ───────────────────────────
    print("\n[3/4] Running planning_crew.run_planning() with mocked agents...")
    try:
        with patch.object(po_agent, "run") as mock_po, \
             patch.object(architect_agent, "run") as mock_arch:

            # Round 1 outputs
            mock_po.side_effect = [_MOCK_PO_OUTPUT_1, _MOCK_PO_CHALLENGE]
            mock_arch.side_effect = [_MOCK_ARCH_OUTPUT_1, _MOCK_ARCH_FINAL]

            plan_path = planning_crew.run_planning("F1.7")
            print(f"      OK: plan written to {plan_path}")

    except Exception as e:
        failures.append(f"run_planning() raised: {e}")
        print(f"      FAIL: {e}")
        import traceback
        traceback.print_exc()
        plan_path = None

    # ── Step 4: Validate plan file ────────────────────────────────────────
    print("\n[4/4] Validating plan content...")
    if plan_path:
        full_path = REPO_ROOT / plan_path
        if not full_path.exists():
            failures.append(f"Plan file not written: {plan_path}")
        else:
            content = full_path.read_text(encoding="utf-8")
            print(f"      Plan: {len(content)} chars at {plan_path}")

            # Check all 8 sections
            for section in _PLAN_SECTIONS:
                num = section.replace("## ", "").replace(".", "").strip()
                pattern = rf"##\s*{re.escape(num)}\."
                if not re.search(pattern, content, re.IGNORECASE):
                    failures.append(f"Missing section: {section}")
                    print(f"      FAIL: missing {section}")
                else:
                    print(f"      OK: {section}")

            # Check real Blue Steel paths
            found = [p for p in _BLUE_STEEL_PATHS if p in content]
            if found:
                print(f"      OK: real paths found: {found}")
            else:
                failures.append("No real Blue Steel paths found in plan")
                print(f"      FAIL: no real paths (expected one of {_BLUE_STEEL_PATHS})")

            # Check no placeholders
            for pattern in [r"src/your[-_]module", r"your[-_]package", r"/path/to/"]:
                if re.search(pattern, content, re.IGNORECASE):
                    failures.append(f"Placeholder found: {pattern}")
                    print(f"      FAIL: placeholder pattern: {pattern}")

    # ── Summary ───────────────────────────────────────────────────────────
    print(f"\n{'='*70}")
    if failures:
        print("STRUCTURAL TEST FAILED:")
        for f in failures:
            print(f"  x {f}")
        sys.exit(1)
    else:
        print("STRUCTURAL TEST PASSED")
        print("  All modules import correctly")
        print("  Both agents construct without errors")
        print("  run_planning() writes a valid 8-section plan")
        print("  Plan contains real Blue Steel paths")
        print(f"\nFor full integration testing (real LLM):")
        print(f"  PIPELINE_MODE=local python test_planning.py  # requires Ollama")
        print(f"  ANTHROPIC_API_KEY=sk-ant-... python test_planning.py  # cloud")
    print(f"{'='*70}\n")


if __name__ == "__main__":
    main()
