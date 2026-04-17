---
name: ci-cd
description: >
  Use this skill whenever you are working on the GitHub Actions CI/CD pipelines, Docker builds,
  or deployment configuration for Blue Steel. Triggers include: "GitHub Actions", "CI workflow",
  "backend.yml", "frontend.yml", "Docker", "deploy", "build pipeline", "path filter", "CI fails",
  "Docker image", "Vercel deployment", "Oracle Cloud VM", "secrets", or any task touching
  `.github/workflows/`. This skill covers both path-filtered workflows, the Docker build target
  (linux/arm64), Vercel auto-deploy integration, and secret management discipline.
---

# CI/CD — GitHub Actions Workflows and Deployment

Blue Steel has two path-filtered GitHub Actions workflows — one per project in the monorepo.
Each workflow covers only its own project. The backend builds a linux/arm64 Docker image deployed
to an Oracle Cloud ARM VM. The frontend auto-deploys to Vercel via GitHub integration. Secrets
are never committed.

## Context

**Relevant decisions:**
- D-044: Environment model — local and prod only; no staging
- D-045: Frontend hosting — Vercel free tier; auto-deploys on push to `main`
- D-046: Backend hosting — Oracle Cloud Always Free ARM VM
- D-048: CI/CD — GitHub Actions with path-filtered workflows
- D-050: Secret management — `.env` on Oracle VM, never committed
- D-065: Commit message format — Conventional Commits
- D-066: Branch naming — `type/short-description` kebab-case

**Monorepo path filtering:** CI runs per project, not per push. A change in `apps/api/` only
triggers `backend.yml`. A change in `apps/web/` only triggers `frontend.yml`. A change to
root-level docs does not trigger either. This is deliberate to avoid unnecessary CI runs and
deployment rebuilds.

## Workflow: `backend.yml`

**Trigger paths:** `apps/api/**`
**Deploy step:** push to `main` only

**Pipeline order:**

```
type check (javac / mvn compile)
  → unit tests + ArchUnit (mvn test)
    → integration tests — Testcontainers (mvn verify)
      → PITest — domain core (mvn pitest:mutationCoverage, scoped)
        → build JAR (mvn package -DskipTests)
          → Docker buildx — linux/arm64
            → push to ghcr.io
              → SSH deploy to Oracle VM (pull + restart container)
```

The deploy steps (Docker push + SSH deploy) run only on `push` to `main`, not on `pull_request`.

**Key configuration points:**

```yaml
# .github/workflows/backend.yml
name: Backend CI/CD

on:
  push:
    paths: ['apps/api/**']
  pull_request:
    paths: ['apps/api/**']

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'
      - name: Run tests
        run: mvn verify -pl apps/api

  build-and-deploy:
    needs: test
    if: github.ref == 'refs/heads/main' && github.event_name == 'push'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Build JAR
        run: mvn package -DskipTests -pl apps/api

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3
        # linux/arm64 required for Oracle Cloud Always Free ARM VM (D-046)

      - name: Log in to GHCR
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Build and push Docker image
        uses: docker/build-push-action@v5
        with:
          context: apps/api
          platforms: linux/arm64       # ← critical: Oracle ARM VM target
          push: true
          tags: ghcr.io/${{ github.repository }}/api:latest

      - name: Deploy to Oracle VM
        # SSH into Oracle VM, pull latest image, restart the container
        # [Command details TBD before Phase 1]
```

**Docker image target:** Always `linux/arm64`. Oracle Cloud Always Free ARM VMs run on 64-bit ARM
architecture. An `amd64` image will not run on them without emulation overhead.

**Dockerfile location:** `apps/api/Dockerfile` — builds a production JAR-based image.

## Workflow: `frontend.yml`

**Trigger paths:** `apps/web/**`
**Deploy step:** Vercel handles deployment automatically via GitHub integration — CI does not push to Vercel.

**Pipeline order:**

```
type check (tsc --noEmit)
  → lint
    → Vitest (unit + component tests)
      → vite build (production build verification)
```

The build step is run in CI to catch build-time errors (Vite config issues, import errors, etc.)
even though Vercel also runs a build. Failing early in CI is faster than waiting for a Vercel
build failure.

```yaml
# .github/workflows/frontend.yml
name: Frontend CI

on:
  push:
    paths: ['apps/web/**']
  pull_request:
    paths: ['apps/web/**']

jobs:
  ci:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'
          cache-dependency-path: apps/web/package-lock.json

      - name: Install dependencies
        run: npm ci
        working-directory: apps/web

      - name: Type check
        run: npm run type-check
        working-directory: apps/web

      - name: Lint
        run: npm run lint
        working-directory: apps/web

      - name: Test
        run: npm test
        working-directory: apps/web

      - name: Build
        run: npm run build
        working-directory: apps/web
        env:
          VITE_API_BASE_URL: 'https://placeholder.for.build.check'
```

**Vercel deployment:** Configured in the Vercel dashboard connected to this GitHub repo.
Vercel auto-deploys `main` to production and creates branch preview URLs for every PR.
CI does not push to Vercel — Vercel's GitHub integration handles it.

## Secrets and Environment Variables

**Required secrets in GitHub Actions:**

| Secret name | Used by | Purpose |
|---|---|---|
| `GITHUB_TOKEN` | backend.yml | Built-in; authenticates push to ghcr.io |
| `ORACLE_SSH_KEY` | backend.yml | Private key for SSH to Oracle VM |
| `ORACLE_VM_HOST` | backend.yml | Oracle VM IP/hostname |
| `ORACLE_VM_USER` | backend.yml | SSH user on Oracle VM |

**Secrets never in code (D-050):**

| Variable | Where it lives |
|---|---|
| `DATABASE_URL` | `.env` on Oracle VM, Neon dashboard |
| `ANTHROPIC_API_KEY` | `.env` on Oracle VM |
| `OPENAI_API_KEY` | `.env` on Oracle VM |
| `JWT_SECRET` | `.env` on Oracle VM |
| `EMAIL_API_KEY` | `.env` on Oracle VM |

**Rules:**
- `.env` and `.env.local` are always in `.gitignore` — verify before every commit
- GitHub Secrets are accessed via `${{ secrets.NAME }}` — never hardcoded in workflow YAML
- Test fixtures and example files must not contain real API keys (D-050)
- Vercel environment variables are configured in the Vercel dashboard, not in CI

## Branch and Commit Conventions (D-065, D-066)

**Branch naming:** `type/short-description` in kebab-case using Conventional Commit types:

```
feat/session-ingestion
fix/jwt-refresh-rotation
chore/liquibase-baseline
refactor/actor-domain-model
test/extraction-pipeline-it
docs/architecture-update
ci/backend-path-filter
```

**Commit message format (Conventional Commits):**

```
type(scope): description

[optional body — explains WHY, not WHAT]

[optional footer]
Co-Authored-By: ...
BREAKING CHANGE: ...
```

Allowed types: `feat`, `fix`, `refactor`, `test`, `chore`, `docs`, `ci`
Allowed scopes: `api`, `web`, `db`, `ci`, `docs`

Examples:
```
feat(api): add session ingestion endpoint
fix(web): disable commit button until UNCERTAIN cards resolved
chore(db): add baseline Liquibase changelog
ci: add path filter for backend workflow
```

## Adding a New CI Check

1. Decide which workflow it belongs to: does it apply to `apps/api/` changes or `apps/web/` changes?
2. Add the step to the correct workflow file.
3. If the check should block PRs: add it to the `test` / `ci` job (which runs on both push and PR).
4. If it is deploy-only: add it to the `build-and-deploy` job (which runs only on push to main).
5. Test the workflow locally with `act` (GitHub Actions local runner) before pushing:
   ```bash
   act -j test --dryrun  # dry run
   act -j test           # real run (requires Docker)
   ```

## Pull Request Workflow

1. Create a branch from `main`: `git checkout -b feat/my-feature`
2. Write code, commit with Conventional Commits format
3. Push and open a PR — Vercel creates a preview URL automatically
4. CI runs the path-filtered workflow for changed paths
5. Review, address feedback
6. Squash or merge to `main` — deploy steps run automatically on merge

## Common Pitfalls

- **Building the Docker image for `linux/amd64`.** The Oracle Cloud ARM VM requires `linux/arm64`.
  A `linux/amd64` image will run via emulation (if `qemu` is installed) but performs very poorly.
  Always specify `platforms: linux/arm64` in the build-push action.

- **Pushing to Vercel from CI manually.** Vercel is wired to the GitHub repo directly. Manually
  deploying from CI conflicts with Vercel's own build queue and can cause race conditions.

- **Committing secrets in test fixtures.** Any file with API keys, JWTs, or passwords committed
  to the repo is a D-050 violation. GitHub secret scanning will flag it. Rotate the key immediately
  if this happens.

- **Triggering the wrong workflow by editing root-level files.** Path filters are exact. A change
  to `README.md` does not trigger either workflow. If CI is not running when expected, check the
  path filter against the changed files.

- **Not setting the `working-directory` for frontend workflow steps.** `apps/web/` is a
  subdirectory. Without `working-directory: apps/web`, `npm ci` runs at the repo root where
  there is no `package.json`.

- **Using `mvn test` instead of `mvn verify` in CI.** `mvn test` runs unit tests only. `mvn verify`
  runs unit tests AND integration tests (Testcontainers). Integration tests must run in CI.

## References

- `CLAUDE.md` §5 (commit format, branch naming)
- `DECISIONS.md` D-044, D-045, D-046, D-048, D-050, D-065, D-066
- `apps/api/CLAUDE.md` §3 (backend run commands)
- `apps/web/CLAUDE.md` §3 (frontend run commands)
