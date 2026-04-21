#!/usr/bin/env bash
# Runs the same checks GitHub Actions runs for a PR.
# Path-filtered: backend checks only if apps/api/ changed, frontend only if apps/web/ changed.
#
# Usage:
#   ./scripts/check-pr.sh              # auto-detect changed files vs origin/main
#   ./scripts/check-pr.sh --all        # force-run all checks regardless of changed paths
#   ./scripts/check-pr.sh --backend    # force-run backend only
#   ./scripts/check-pr.sh --frontend   # force-run frontend only

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BOLD='\033[1m'
RESET='\033[0m'

pass() { echo -e "${GREEN}✓${RESET} $1"; }
fail() { echo -e "${RED}✗${RESET} $1"; exit 1; }
header() { echo -e "\n${BOLD}${YELLOW}▶ $1${RESET}"; }

# ── Determine which paths changed ────────────────────────────────────────────

FORCE_BACKEND=false
FORCE_FRONTEND=false

for arg in "$@"; do
  case "$arg" in
    --all)      FORCE_BACKEND=true; FORCE_FRONTEND=true ;;
    --backend)  FORCE_BACKEND=true ;;
    --frontend) FORCE_FRONTEND=true ;;
  esac
done

if ! $FORCE_BACKEND && ! $FORCE_FRONTEND; then
  MERGE_BASE=$(git merge-base HEAD origin/main 2>/dev/null || git merge-base HEAD main 2>/dev/null || echo "")
  if [ -n "$MERGE_BASE" ]; then
    CHANGED=$(git diff --name-only "$MERGE_BASE" HEAD)
  else
    CHANGED=$(git diff --name-only HEAD~1 HEAD 2>/dev/null || git diff --name-only HEAD)
  fi

  echo "$CHANGED" | grep -q "^apps/api/" && FORCE_BACKEND=true || true
  echo "$CHANGED" | grep -q "^apps/web/" && FORCE_FRONTEND=true || true

  if ! $FORCE_BACKEND && ! $FORCE_FRONTEND; then
    echo "No changes in apps/api/ or apps/web/ — nothing to check."
    exit 0
  fi
fi

FAILED=false

# ── Backend checks (mirrors backend.yml) ─────────────────────────────────────

run_backend() {
  header "Backend CI checks (apps/api)"
  cd "$REPO_ROOT/apps/api"

  echo "  [1/4] spotless:check"
  mvn --no-transfer-progress spotless:check \
    || { fail "spotless:check failed — run: mvn spotless:apply -pl apps/api"; return 1; }
  pass "spotless:check"

  echo "  [2/4] compile"
  mvn --no-transfer-progress compile -DskipTests -q \
    || { fail "compile failed"; return 1; }
  pass "compile"

  echo "  [3/4] test (unit + ArchUnit + Testcontainers IT)"
  mvn --no-transfer-progress verify -q \
    || { fail "test / integration-test failed"; return 1; }
  pass "verify (59 unit + 19 integration)"

  echo "  [4/4] pitest:mutationCoverage"
  mvn --no-transfer-progress test-compile pitest:mutationCoverage -q \
    || { fail "mutation score below 80% threshold"; return 1; }
  pass "pitest:mutationCoverage"

  cd "$REPO_ROOT"
}

# ── Frontend checks (mirrors frontend.yml) ────────────────────────────────────

run_frontend() {
  header "Frontend CI checks (apps/web)"
  cd "$REPO_ROOT/apps/web"

  echo "  [1/5] npm audit"
  npm audit --audit-level=high --production \
    || { fail "npm audit: high/critical vulnerability found"; return 1; }
  pass "npm audit"

  echo "  [2/5] type-check"
  npm run type-check \
    || { fail "TypeScript type errors"; return 1; }
  pass "type-check"

  echo "  [3/5] lint"
  npm run lint \
    || { fail "ESLint errors"; return 1; }
  pass "lint"

  echo "  [4/5] test"
  npm test \
    || { fail "Vitest tests failed"; return 1; }
  pass "test"

  echo "  [5/5] build"
  VITE_API_BASE_URL=https://placeholder.for.build.check npm run build \
    || { fail "Vite build failed"; return 1; }
  pass "build"

  cd "$REPO_ROOT"
}

# ── Run ───────────────────────────────────────────────────────────────────────

if $FORCE_BACKEND; then
  run_backend || FAILED=true
fi

if $FORCE_FRONTEND; then
  run_frontend || FAILED=true
fi

echo ""
if $FAILED; then
  echo -e "${RED}${BOLD}✗ PR checks FAILED — fix the issues above before pushing.${RESET}"
  exit 1
else
  echo -e "${GREEN}${BOLD}✓ All PR checks passed — safe to push.${RESET}"
fi
