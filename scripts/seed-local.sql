-- =============================================================================
-- scripts/seed-local.sql  —  LOCAL-ONLY manual-test data for Blue Steel
-- =============================================================================
-- Deletes the seeded users/campaigns/memberships below and recreates them, so
-- it is safe to run repeatedly to RESET local test state. The bootstrapped
-- admin (admin@local.dev) and any campaigns you create through the UI are left
-- untouched.
--
-- All seeded users share the password:   Bluesteel!2026
--
--   admin@local.dev  / Admin!Local123456  (bootstrapped by the app, not here)
--   gm@local.dev     — gm of Curse of Strahd + Lost Mine of Phandelver
--   editor@local.dev — editor of Curse of Strahd
--   player@local.dev — player of Curse of Strahd + Tomb of Annihilation
--   multi@local.dev  — player (Strahd), editor (Phandelver), gm (Tomb)
--   lonely@local.dev — no campaigns (non-admin empty state / 403 checks)
--   newbie@local.dev — force_password_change = true (forced-change flow)
--
-- PREREQUISITES
--   1. Local Postgres running:   podman compose up -d     (or: docker compose up -d)
--   2. Backend started ONCE with the `local` profile so Liquibase built the
--      schema and the admin was bootstrapped (this script's campaigns are
--      created_by that admin; without it the run aborts).
--
-- HOW TO RUN  (Windows PowerShell, from the repo root)
--   PowerShell has no `<` redirection, so pipe the file in with Get-Content -Raw:
--
--     Get-Content scripts/seed-local.sql -Raw | podman compose exec -T postgres `
--       psql -U bluesteel -d bluesteel -v ON_ERROR_STOP=1
--
--   Using Docker instead of Podman, swap the engine name:
--
--     Get-Content scripts/seed-local.sql -Raw | docker compose exec -T postgres `
--       psql -U bluesteel -d bluesteel -v ON_ERROR_STOP=1
--
--   Run from the directory containing docker-compose.yml. The `postgres` service
--   and the bluesteel db/user/password all come from that compose file.
-- =============================================================================

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1) Clean up any previous seed (admin + UI-created campaigns are untouched) ---
DELETE FROM campaign_members
 WHERE campaign_id IN (
         'c0000000-0000-4000-8000-000000000001',
         'c0000000-0000-4000-8000-000000000002',
         'c0000000-0000-4000-8000-000000000003')
    OR user_id IN (SELECT id FROM users WHERE email IN (
         'gm@local.dev','editor@local.dev','player@local.dev',
         'multi@local.dev','lonely@local.dev','newbie@local.dev'));

DELETE FROM campaigns
 WHERE id IN (
         'c0000000-0000-4000-8000-000000000001',
         'c0000000-0000-4000-8000-000000000002',
         'c0000000-0000-4000-8000-000000000003');

DELETE FROM users
 WHERE email IN (
         'gm@local.dev','editor@local.dev','player@local.dev',
         'multi@local.dev','lonely@local.dev','newbie@local.dev');

-- 2) Users (admin@local.dev is bootstrapped by the app, not here) --------------
INSERT INTO users (id, email, password_hash, is_admin, force_password_change, created_at) VALUES
  ('a0000000-0000-4000-8000-000000000001','gm@local.dev',     crypt('Bluesteel!2026', gen_salt('bf',10)), false, false, now()),
  ('a0000000-0000-4000-8000-000000000002','editor@local.dev', crypt('Bluesteel!2026', gen_salt('bf',10)), false, false, now()),
  ('a0000000-0000-4000-8000-000000000003','player@local.dev', crypt('Bluesteel!2026', gen_salt('bf',10)), false, false, now()),
  ('a0000000-0000-4000-8000-000000000004','multi@local.dev',  crypt('Bluesteel!2026', gen_salt('bf',10)), false, false, now()),
  ('a0000000-0000-4000-8000-000000000005','lonely@local.dev', crypt('Bluesteel!2026', gen_salt('bf',10)), false, false, now()),
  ('a0000000-0000-4000-8000-000000000006','newbie@local.dev', crypt('Bluesteel!2026', gen_salt('bf',10)), false, true,  now());

-- 3) Campaigns (created_by = bootstrapped admin) ------------------------------
INSERT INTO campaigns (id, name, created_by, created_at) VALUES
  ('c0000000-0000-4000-8000-000000000001','Curse of Strahd',         (SELECT id FROM users WHERE email='admin@local.dev'), now()),
  ('c0000000-0000-4000-8000-000000000002','Lost Mine of Phandelver', (SELECT id FROM users WHERE email='admin@local.dev'), now()),
  ('c0000000-0000-4000-8000-000000000003','Tomb of Annihilation',    (SELECT id FROM users WHERE email='admin@local.dev'), now());

-- 4) Memberships ---------------------------------------------------------------
INSERT INTO campaign_members (id, campaign_id, user_id, role, joined_at) VALUES
  ('11110000-0000-4000-8000-000000000001','c0000000-0000-4000-8000-000000000001','a0000000-0000-4000-8000-000000000001','gm',     now()),
  ('11110000-0000-4000-8000-000000000002','c0000000-0000-4000-8000-000000000001','a0000000-0000-4000-8000-000000000002','editor', now()),
  ('11110000-0000-4000-8000-000000000003','c0000000-0000-4000-8000-000000000001','a0000000-0000-4000-8000-000000000003','player', now()),
  ('11110000-0000-4000-8000-000000000004','c0000000-0000-4000-8000-000000000001','a0000000-0000-4000-8000-000000000004','player', now()),
  ('11110000-0000-4000-8000-000000000005','c0000000-0000-4000-8000-000000000002','a0000000-0000-4000-8000-000000000001','gm',     now()),
  ('11110000-0000-4000-8000-000000000006','c0000000-0000-4000-8000-000000000002','a0000000-0000-4000-8000-000000000004','editor', now()),
  ('11110000-0000-4000-8000-000000000007','c0000000-0000-4000-8000-000000000003','a0000000-0000-4000-8000-000000000004','gm',     now()),
  ('11110000-0000-4000-8000-000000000008','c0000000-0000-4000-8000-000000000003','a0000000-0000-4000-8000-000000000003','player', now());

COMMIT;
