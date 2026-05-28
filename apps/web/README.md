# Blue Steel — Frontend

React 19 / Vite / TypeScript SPA deployed to Vercel. For full-stack local dev see
[`README.md`](../../README.md) at the repo root.

---

## Environment variables

Vite reads `.env.local` from `apps/web/` (gitignored). One variable is used:

| Variable | Purpose | Local value | Production |
|---|---|---|---|
| `VITE_API_BASE_URL` | Backend base URL for all API calls | `http://localhost:8080` | Set in Vercel dashboard |

Copy `.env.example` to `.env.local` to get started:

```bash
cp .env.example .env.local
```

---

## Vercel deployment

Deployment is triggered automatically by Vercel's GitHub integration on push to `main`.
PR pushes generate ephemeral preview URLs automatically. No GitHub Actions deploy step is needed.

**Required Vercel environment variable:**

| Variable | Value | Scopes |
|---|---|---|
| `VITE_API_BASE_URL` | Production backend URL (e.g. `https://api.yourdomain.com`) | Production, Preview, Development |

**`vercel.json`** in this directory provides the SPA rewrite: all paths serve `index.html` so
React Router handles routing. Without it, a hard refresh on any route returns a Vercel 404.

For the one-time Vercel + GitHub setup steps, see [`SETUP_CHECKLIST.md`](../../SETUP_CHECKLIST.md)
at the repo root.

---

## CI

`.github/workflows/frontend.yml` runs on every push to a branch that touches `apps/web/**`:

```
npm audit → type-check → lint → test → build
```

The build step uses a placeholder `VITE_API_BASE_URL` so the build succeeds without a live backend.
Vercel handles deployment — not this workflow.

---

## Development commands

```bash
npm run dev          # dev server (http://localhost:5173)
npm run type-check   # TypeScript — tsc --noEmit
npm run lint         # ESLint
npm test             # Vitest (CI mode)
npm run build        # production build → dist/
```
