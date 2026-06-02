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
-- CAMPAIGN STATES (for testing the session history view, F2.13):
--   Curse of Strahd   — 3 committed, 1 failed, 1 active draft (Resume/Discard)
--   Lost Mine of Phandelver — 1 committed (short history / single-item list)
--   Tomb of Annihilation   — 0 sessions (empty state "No sessions yet")
--
-- WORLD STATE (Curse of Strahd, committed sessions 1-3):
--   Actors  — Strahd, Ireena, Ismark (s1); Lady Wachter, Baron Vallakovich (s3)
--   Spaces  — Village of Barovia, Castle Ravenloft (s1); Vallaki (s3)
--   Events  — Arrival, Zombie horde (s1); Werewolf ambush (s2); Festival (s3)
--   Relations — Strahd→Ireena obsession, Ireena→Ismark sibling bond (s1)
--
-- DRAFT diff_payload (Curse of Strahd session 5):
--   Actors  — NEW: Madame Eva; EXISTING update: Strahd; UNCERTAIN: dark figure
--   Spaces  — NEW: Tser Pool Vistani Camp
--   Events  — NEW: Madame Eva reads the Tarokka cards
--   Conflicts — Strahd's location contradiction
--   → Lets you test the full diff-review UI: all four card types + conflict ack
--   → The UNCERTAIN card blocks commit until resolved (D-042)
--
-- UUID namespaces (all stable across re-runs; only hex digits 0-9/a-f allowed):
--   a00...   users          c00...   campaigns      111...   memberships
--   e00...   sessions       b00...   narrative_blocks
--   ac0...   actors         a10...   actor_versions
--   a20...   spaces         a30...   space_versions
--   a40...   events         ee0...   event_versions
--   a60...   relations      a70...   relation_versions
--   d00...   diff card IDs (inside diff_payload JSON only)
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

-- 1) Clean up any previous seed -------------------------------------------------
-- Deleting the campaigns cascades (via ON DELETE CASCADE, migrations 0020-0022):
--   campaigns → sessions → narrative_blocks
--                        → actors → actor_versions
--                        → spaces → space_versions
--                        → events → event_versions
--                        → relations → relation_versions
--
-- So we only need to explicitly clean campaign_members, campaigns, and users.

DELETE FROM refresh_tokens
 WHERE user_id IN (SELECT id FROM users WHERE email IN (
         'gm@local.dev','editor@local.dev','player@local.dev',
         'multi@local.dev','lonely@local.dev','newbie@local.dev'));

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

-- 2) Users (admin@local.dev is bootstrapped by the app, not here) ---------------
INSERT INTO users (id, email, password_hash, is_admin, force_password_change, created_at) VALUES
  ('a0000000-0000-4000-8000-000000000001','gm@local.dev',     crypt('Bluesteel!2026', gen_salt('bf',10)), false, false, now()),
  ('a0000000-0000-4000-8000-000000000002','editor@local.dev', crypt('Bluesteel!2026', gen_salt('bf',10)), false, false, now()),
  ('a0000000-0000-4000-8000-000000000003','player@local.dev', crypt('Bluesteel!2026', gen_salt('bf',10)), false, false, now()),
  ('a0000000-0000-4000-8000-000000000004','multi@local.dev',  crypt('Bluesteel!2026', gen_salt('bf',10)), false, false, now()),
  ('a0000000-0000-4000-8000-000000000005','lonely@local.dev', crypt('Bluesteel!2026', gen_salt('bf',10)), false, false, now()),
  ('a0000000-0000-4000-8000-000000000006','newbie@local.dev', crypt('Bluesteel!2026', gen_salt('bf',10)), false, true,  now());

-- 3) Campaigns (created_by = bootstrapped admin) --------------------------------
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

-- =============================================================================
-- CURSE OF STRAHD — Sessions
-- Status values are lowercase in the DB (Java enum serialised by name, lowercased
-- by the sessions.chk_sessions_status constraint).
-- =============================================================================

-- 5a) Sessions -----------------------------------------------------------------
-- sequence_number is assigned only on commit (NULL for draft/failed/discarded).
-- diff_payload is populated only while the session is in draft; cleared on commit.
-- owner_id = gm (the user who submitted each session).

INSERT INTO sessions
  (id, campaign_id, owner_id, sequence_number, status, diff_payload,
   failure_reason, committed_at, created_at, updated_at)
VALUES

  -- Session 1: committed — party arrives in Barovia, meets Strahd's domain
  ('e0000000-0000-4000-8000-000000000001',
   'c0000000-0000-4000-8000-000000000001',
   'a0000000-0000-4000-8000-000000000001',
   1, 'committed', NULL, NULL,
   '2026-01-11 19:00:00+00',
   '2026-01-10 14:00:00+00',
   '2026-01-11 19:00:00+00'),

  -- Session 2: committed — village exploration, first ally contacts
  ('e0000000-0000-4000-8000-000000000002',
   'c0000000-0000-4000-8000-000000000001',
   'a0000000-0000-4000-8000-000000000001',
   2, 'committed', NULL, NULL,
   '2026-01-18 20:00:00+00',
   '2026-01-17 14:00:00+00',
   '2026-01-18 20:00:00+00'),

  -- Session 3: committed — road to Vallaki, burgomaster encountered
  ('e0000000-0000-4000-8000-000000000003',
   'c0000000-0000-4000-8000-000000000001',
   'a0000000-0000-4000-8000-000000000001',
   3, 'committed', NULL, NULL,
   '2026-01-25 21:00:00+00',
   '2026-01-24 14:00:00+00',
   '2026-01-25 21:00:00+00'),

  -- Session 4: failed — pipeline error (tests the FAILED badge in the UI)
  ('e0000000-0000-4000-8000-000000000004',
   'c0000000-0000-4000-8000-000000000001',
   'a0000000-0000-4000-8000-000000000001',
   NULL, 'failed', NULL,
   'PIPELINE_NOT_IMPLEMENTED',
   NULL,
   '2026-02-01 14:00:00+00',
   '2026-02-01 14:05:00+00'),

  -- Session 5: draft — Madame Eva and the Tarokka reading
  -- diff_payload has all four card types so the full diff-review UI can be tested:
  --   EXISTING (Strahd update), NEW (Madame Eva), UNCERTAIN (dark figure),
  --   plus a ConflictCard on Strahd's location.
  -- The UNCERTAIN card blocks commit until the GM resolves it (D-042).
  ('e0000000-0000-4000-8000-000000000005',
   'c0000000-0000-4000-8000-000000000001',
   'a0000000-0000-4000-8000-000000000001',
   NULL, 'draft',
   '{
     "narrativeSummaryHeader": "The party reaches Tser Pool and Madame Eva reads their fate from the Tarokka deck, revealing where powerful artefacts lie hidden across Barovia.",
     "actors": [
       {
         "cardType": "EXISTING",
         "cardId":   "d0000000-0000-4000-8000-000000000001",
         "entityId": "ac000000-0000-4000-8000-000000000001",
         "entityType": "actor",
         "name": "Count Strahd von Zarovich",
         "changedFields": {
           "description": "The party now understands the full scope of Strahd''s curse — he cannot leave Barovia and hungers to reclaim his mortality through Ireena."
         }
       },
       {
         "cardType": "NEW",
         "cardId":   "d0000000-0000-4000-8000-000000000002",
         "entityType": "actor",
         "name": "Madame Eva",
         "fullProfile": {
           "description": "Ancient Vistani seer of Tser Pool, keeper of the Tarokka deck. She reveals the locations of the Sunsword, the Holy Symbol of Ravenkind, and the Tome of Strahd to the party.",
           "role": "Neutral guide",
           "aliases": ["The Ancient"],
           "status": "Alive"
         }
       },
       {
         "cardType": "UNCERTAIN",
         "cardId":   "d0000000-0000-4000-8000-000000000003",
         "entityType": "actor",
         "extractedMention": "the dark figure watching from the tree line",
         "candidateEntityId": "ac000000-0000-4000-8000-000000000001"
       }
     ],
     "spaces": [
       {
         "cardType": "NEW",
         "cardId":   "d0000000-0000-4000-8000-000000000004",
         "entityType": "space",
         "name": "Tser Pool Vistani Camp",
         "fullProfile": {
           "description": "A lakeside encampment of Vistani wagons surrounding a bonfire at Tser Pool, home of Madame Eva and her tribe.",
           "type": "Encampment",
           "status": "Active"
         }
       }
     ],
     "events": [
       {
         "cardType": "NEW",
         "cardId":   "d0000000-0000-4000-8000-000000000005",
         "entityType": "event",
         "name": "Madame Eva reads the Tarokka cards",
         "fullProfile": {
           "description": "Madame Eva performs the Tarokka card reading, revealing three artefacts the party must gather to defeat Strahd and the location of a great ally.",
           "outcome": "Party learns artefact locations and the identity of a potential ally"
         }
       }
     ],
     "relations": [],
     "detectedConflicts": [
       {
         "conflictId": "d0000000-0000-4000-8000-000000000006",
         "entityId":   "ac000000-0000-4000-8000-000000000001",
         "entityType": "actor",
         "description": "Strahd''s location is contradicted by this session",
         "extractedFact": "Strahd is seen watching the party from the tree line near Tser Pool",
         "existingFact":  "Strahd is known to reside in Castle Ravenloft above the village"
       }
     ]
   }',
   NULL, NULL,
   '2026-02-08 14:00:00+00',
   '2026-02-08 14:30:00+00');

-- =============================================================================
-- LOST MINE OF PHANDELVER — Sessions
-- =============================================================================

INSERT INTO sessions
  (id, campaign_id, owner_id, sequence_number, status, diff_payload,
   failure_reason, committed_at, created_at, updated_at)
VALUES
  -- Session 1: committed — the ambush on the Triboar Trail
  ('e0000000-0000-4000-8000-000000000010',
   'c0000000-0000-4000-8000-000000000002',
   'a0000000-0000-4000-8000-000000000001',
   1, 'committed', NULL, NULL,
   '2026-01-15 20:00:00+00',
   '2026-01-14 14:00:00+00',
   '2026-01-15 20:00:00+00');

-- =============================================================================
-- NARRATIVE BLOCKS (raw session text, stored once per session after ingestion)
-- =============================================================================

INSERT INTO narrative_blocks (id, session_id, raw_summary_text, token_count, created_at) VALUES

  ('b0000000-0000-4000-8000-000000000001',
   'e0000000-0000-4000-8000-000000000001',
   'Session 1 — Arrival at Barovia

The party crossed the mysterious fog and entered the darkened village of Barovia. The streets were silent, the villagers terrified and shuttered inside. They found two young children, Rose and Thorn Durst, beckoning them toward a large manor house. In the town square they encountered their first zombie horde — shambling figures that the village folk seemed resigned to. Ismark Kolyanovich found the party and led them to his father''s home, where they met his sister Ireena Kolyana. The burgomaster''s body lay dead in the house. Ismark begged the party to escort Ireena away from Barovia''s village — Strahd had visited her twice already and marked her as his. Through the mist above the valley, Castle Ravenloft loomed on its impossibly tall spur of rock.',
   312,
   '2026-01-10 14:00:00+00'),

  ('b0000000-0000-4000-8000-000000000002',
   'e0000000-0000-4000-8000-000000000002',
   'Session 2 — The Road from the Village

After burying the burgomaster in the church graveyard, the party agreed to escort Ireena to Vallaki, a walled town to the west said to be safer than the village. They gathered supplies from Ismark and set out on the Svalich Road. On the mountain pass a werewolf ambushed the party under a heavy fog — it tore into the fighter before the rogue drove it off with silvered arrows. Ismark admitted he feared his sister was the reincarnation of Strahd''s long-dead love, Tatyana, which was why the vampire lord obsessed over her.',
   198,
   '2026-01-17 14:00:00+00'),

  ('b0000000-0000-4000-8000-000000000003',
   'e0000000-0000-4000-8000-000000000003',
   'Session 3 — Vallaki: City of Festivities

The party arrived in Vallaki, a walled town ruled by the self-important Baron Vargas Vallakovich who mandated weekly festivals to maintain "happiness" by force. Lady Fiona Wachter, a rival noble, secretly despised the Baron and made veiled contact with the party, suggesting he was a tyrant worth removing. The current festival — the Festival of the Blazing Sun — was being prepared with forced participation. The party found an inn and began gathering information about Strahd, the artefacts they might need to defeat him, and the political tensions in town.',
   221,
   '2026-01-24 14:00:00+00'),

  ('b0000000-0000-4000-8000-000000000010',
   'e0000000-0000-4000-8000-000000000010',
   'Session 1 — Ambush on the Triboar Trail

The party was hired by the dwarf Gundren Rockseeker to escort a wagon of supplies to Phandalin. On the Triboar Trail they discovered two dead horses and were ambushed by goblins. Following the goblin trail to Cragmaw Hideout, they rescued Sildar Hallwinter (a member of the Lords'' Alliance) but learned that Gundren and his map to Wave Echo Cave were taken to Cragmaw Castle. Sildar revealed that Gundren''s brothers had already reached the cave and begun excavating the long-lost Forge of Spells.',
   189,
   '2026-01-14 14:00:00+00');

-- =============================================================================
-- WORLD STATE — Curse of Strahd
-- All world-state entities are owned by the gm user (a000...001) and belong to
-- campaign c000...001. Version history is append-only (D-001).
-- =============================================================================

-- 6a) Actors -------------------------------------------------------------------

INSERT INTO actors (id, campaign_id, owner_id, name, created_at, created_in_session_id) VALUES
  ('ac000000-0000-4000-8000-000000000001', 'c0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000001',
   'Count Strahd von Zarovich',  '2026-01-11 19:00:00+00', 'e0000000-0000-4000-8000-000000000001'),
  ('ac000000-0000-4000-8000-000000000002', 'c0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000001',
   'Ireena Kolyana',             '2026-01-11 19:00:00+00', 'e0000000-0000-4000-8000-000000000001'),
  ('ac000000-0000-4000-8000-000000000003', 'c0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000001',
   'Ismark Kolyanovich',         '2026-01-11 19:00:00+00', 'e0000000-0000-4000-8000-000000000001'),
  ('ac000000-0000-4000-8000-000000000004', 'c0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000001',
   'Lady Fiona Wachter',         '2026-01-25 21:00:00+00', 'e0000000-0000-4000-8000-000000000003'),
  ('ac000000-0000-4000-8000-000000000005', 'c0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000001',
   'Baron Vargas Vallakovich',   '2026-01-25 21:00:00+00', 'e0000000-0000-4000-8000-000000000003');

-- 6b) Actor versions -----------------------------------------------------------
-- full_snapshot: complete entity state at this version.
-- changed_fields: only the fields that changed (NULL for the initial version).

INSERT INTO actor_versions
  (id, actor_id, session_id, version_number, changed_fields, full_snapshot, created_at)
VALUES

  -- Strahd v1 (session 1 — first introduction)
  ('a1000000-0000-4000-8000-000000000001',
   'ac000000-0000-4000-8000-000000000001',
   'e0000000-0000-4000-8000-000000000001',
   1, NULL,
   '{"name":"Count Strahd von Zarovich","description":"The undead lord of Barovia, an ancient vampire who rules the dark land from his castle. He is obsessed with Ireena Kolyana, whom he believes is a reincarnation of his lost love Tatyana.","role":"Antagonist","aliases":["The Devil Strahd","Lord of Barovia"],"status":"Undead/Active"}',
   '2026-01-11 19:00:00+00'),

  -- Ireena v1 (session 1 — introduced by Ismark)
  ('a1000000-0000-4000-8000-000000000002',
   'ac000000-0000-4000-8000-000000000002',
   'e0000000-0000-4000-8000-000000000001',
   1, NULL,
   '{"name":"Ireena Kolyana","description":"Adopted daughter of the late Burgomaster of Barovia. She bears a striking resemblance to Strahd''s lost love Tatyana, and Strahd has already visited her twice. The party must escort her to safety.","role":"Protected NPC","status":"Alive","location":"Village of Barovia"}',
   '2026-01-11 19:00:00+00'),

  -- Ireena v2 (session 2 — location updated, more detail revealed)
  ('a1000000-0000-4000-8000-000000000003',
   'ac000000-0000-4000-8000-000000000002',
   'e0000000-0000-4000-8000-000000000002',
   2,
   '{"location":"Svalich Road (travelling to Vallaki)","description":"Now travelling with the party on the road to Vallaki. Ismark has confirmed she is believed to be the reincarnation of Tatyana, Strahd''s long-dead love."}',
   '{"name":"Ireena Kolyana","description":"Adopted daughter of the late Burgomaster of Barovia, believed to be the reincarnation of Strahd''s lost love Tatyana. She is now travelling with the party toward Vallaki.","role":"Protected NPC","status":"Alive","location":"Svalich Road (travelling to Vallaki)"}',
   '2026-01-18 20:00:00+00'),

  -- Ismark v1 (session 1)
  ('a1000000-0000-4000-8000-000000000004',
   'ac000000-0000-4000-8000-000000000003',
   'e0000000-0000-4000-8000-000000000001',
   1, NULL,
   '{"name":"Ismark Kolyanovich","description":"Son of the late Burgomaster of Barovia. Known as Ismark the Lesser due to his inability to protect his village. He desperately wants to see his sister Ireena escorted to safety.","role":"Reluctant ally","status":"Alive","location":"Village of Barovia"}',
   '2026-01-11 19:00:00+00'),

  -- Ismark v2 (session 2 — his full backstory becomes clear)
  ('a1000000-0000-4000-8000-000000000005',
   'ac000000-0000-4000-8000-000000000003',
   'e0000000-0000-4000-8000-000000000002',
   2,
   '{"description":"He has now accompanied the party for part of the journey and shared the full truth about Ireena''s connection to Tatyana."}',
   '{"name":"Ismark Kolyanovich","description":"Son of the late Burgomaster of Barovia. He has accompanied the party partway on the road to Vallaki and revealed that Ireena is likely the reincarnation of Strahd''s lost love Tatyana.","role":"Reluctant ally","status":"Alive","location":"Village of Barovia (remained behind)"}',
   '2026-01-18 20:00:00+00'),

  -- Lady Wachter v1 (session 3)
  ('a1000000-0000-4000-8000-000000000006',
   'ac000000-0000-4000-8000-000000000004',
   'e0000000-0000-4000-8000-000000000003',
   1, NULL,
   '{"name":"Lady Fiona Wachter","description":"A Vallakian noblewoman and secret Devil-worshipper who opposes Baron Vallakovich''s rule. She approached the party covertly and suggested the Baron is a tyrant worth removing. Her true motives are unclear.","role":"Ambiguous ally/schemer","status":"Alive","location":"Vallaki"}',
   '2026-01-25 21:00:00+00'),

  -- Baron Vallakovich v1 (session 3)
  ('a1000000-0000-4000-8000-000000000007',
   'ac000000-0000-4000-8000-000000000005',
   'e0000000-0000-4000-8000-000000000003',
   1, NULL,
   '{"name":"Baron Vargas Vallakovich","description":"Self-styled protector of Vallaki who enforces mandatory weekly festivals, believing they ward off Strahd. Any expression of unhappiness in his town is punished. Currently preparing the Festival of the Blazing Sun.","role":"Antagonist/Ruler","status":"Alive","location":"Vallaki — the Blue Water Inn area"}',
   '2026-01-25 21:00:00+00');

-- =============================================================================
-- WORLD STATE — Spaces
-- =============================================================================

INSERT INTO spaces (id, campaign_id, owner_id, name, created_at, created_in_session_id) VALUES
  ('a2000000-0000-4000-8000-000000000001', 'c0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000001',
   'Village of Barovia',  '2026-01-11 19:00:00+00', 'e0000000-0000-4000-8000-000000000001'),
  ('a2000000-0000-4000-8000-000000000002', 'c0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000001',
   'Castle Ravenloft',    '2026-01-11 19:00:00+00', 'e0000000-0000-4000-8000-000000000001'),
  ('a2000000-0000-4000-8000-000000000003', 'c0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000001',
   'Vallaki',             '2026-01-25 21:00:00+00', 'e0000000-0000-4000-8000-000000000003');

INSERT INTO space_versions
  (id, space_id, session_id, version_number, changed_fields, full_snapshot, created_at)
VALUES
  ('a3000000-0000-4000-8000-000000000001',
   'a2000000-0000-4000-8000-000000000001',
   'e0000000-0000-4000-8000-000000000001',
   1, NULL,
   '{"name":"Village of Barovia","description":"A bleak, fog-shrouded village under the shadow of Castle Ravenloft. The terrified populace rarely leaves their homes. The streets are patrolled by undead at night.","type":"Settlement","status":"Under Strahd''s control"}',
   '2026-01-11 19:00:00+00'),

  ('a3000000-0000-4000-8000-000000000002',
   'a2000000-0000-4000-8000-000000000002',
   'e0000000-0000-4000-8000-000000000001',
   1, NULL,
   '{"name":"Castle Ravenloft","description":"An impossibly tall castle perched on a spur of rock above Barovia, visible through the eternal mist. It is the seat of Strahd''s power. The party has only seen it from a distance.","type":"Castle/Fortress","status":"Strahd''s stronghold — not yet entered"}',
   '2026-01-11 19:00:00+00'),

  ('a3000000-0000-4000-8000-000000000003',
   'a2000000-0000-4000-8000-000000000003',
   'e0000000-0000-4000-8000-000000000003',
   1, NULL,
   '{"name":"Vallaki","description":"A walled town on Lake Zarovich ruled with an iron fist by Baron Vallakovich. He mandates weekly festivals to maintain happiness and ward off Strahd. Political tension between the Baron and Lady Wachter simmers.","type":"Settlement","status":"Nominally independent — tense political situation"}',
   '2026-01-25 21:00:00+00');

-- =============================================================================
-- WORLD STATE — Events
-- =============================================================================

INSERT INTO events (id, campaign_id, owner_id, name, created_at, created_in_session_id) VALUES
  ('a4000000-0000-4000-8000-000000000001', 'c0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000001',
   'Party crosses into Barovia',           '2026-01-11 19:00:00+00', 'e0000000-0000-4000-8000-000000000001'),
  ('a4000000-0000-4000-8000-000000000002', 'c0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000001',
   'Zombie horde in the village square',   '2026-01-11 19:00:00+00', 'e0000000-0000-4000-8000-000000000001'),
  ('a4000000-0000-4000-8000-000000000003', 'c0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000001',
   'Werewolf ambush on the Svalich Road',  '2026-01-18 20:00:00+00', 'e0000000-0000-4000-8000-000000000002'),
  ('a4000000-0000-4000-8000-000000000004', 'c0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000001',
   'Festival of the Blazing Sun — Vallaki','2026-01-25 21:00:00+00', 'e0000000-0000-4000-8000-000000000003');

INSERT INTO event_versions
  (id, event_id, session_id, version_number, changed_fields, full_snapshot, created_at)
VALUES
  ('ee000000-0000-4000-8000-000000000001',
   'a4000000-0000-4000-8000-000000000001',
   'e0000000-0000-4000-8000-000000000001',
   1, NULL,
   '{"name":"Party crosses into Barovia","description":"The adventuring party passed through the mist at the border of Barovia and found themselves trapped in the cursed land. The mist closed behind them; there was no return until Strahd is dealt with.","outcome":"Party is now trapped in Barovia"}',
   '2026-01-11 19:00:00+00'),

  ('ee000000-0000-4000-8000-000000000002',
   'a4000000-0000-4000-8000-000000000002',
   'e0000000-0000-4000-8000-000000000001',
   1, NULL,
   '{"name":"Zombie horde in the village square","description":"A shambling horde of zombies appeared in the village square. The townsfolk seemed grimly accustomed to this. The party fought off several zombies before the horde dispersed at dawn.","outcome":"Party survived; zombies dispersed at daylight"}',
   '2026-01-11 19:00:00+00'),

  ('ee000000-0000-4000-8000-000000000003',
   'a4000000-0000-4000-8000-000000000003',
   'e0000000-0000-4000-8000-000000000002',
   1, NULL,
   '{"name":"Werewolf ambush on the Svalich Road","description":"A werewolf leapt from the fog on the Svalich Road and mauled the fighter before the rogue drove it off with silvered arrows. The creature fled into the dark forest.","outcome":"Fighter wounded; werewolf fled; party cautious about the full moon"}',
   '2026-01-18 20:00:00+00'),

  ('ee000000-0000-4000-8000-000000000004',
   'a4000000-0000-4000-8000-000000000004',
   'e0000000-0000-4000-8000-000000000003',
   1, NULL,
   '{"name":"Festival of the Blazing Sun — Vallaki","description":"Baron Vallakovich''s latest mandatory festival was in full preparation. Citizens displayed forced smiles. The party observed the political tension and noted the Baron''s guards harassing residents who seemed unhappy.","outcome":"Party understands Vallaki''s political situation; Lady Wachter made contact"}',
   '2026-01-25 21:00:00+00');

-- =============================================================================
-- WORLD STATE — Relations
-- =============================================================================

INSERT INTO relations (id, campaign_id, owner_id, name, created_at, created_in_session_id) VALUES
  ('a6000000-0000-4000-8000-000000000001', 'c0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000001',
   'Strahd obsesses over Ireena',    '2026-01-11 19:00:00+00', 'e0000000-0000-4000-8000-000000000001'),
  ('a6000000-0000-4000-8000-000000000002', 'c0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000001',
   'Ireena and Ismark are siblings', '2026-01-11 19:00:00+00', 'e0000000-0000-4000-8000-000000000001');

INSERT INTO relation_versions
  (id, relation_id, session_id, version_number, changed_fields, full_snapshot, created_at)
VALUES
  ('a7000000-0000-4000-8000-000000000001',
   'a6000000-0000-4000-8000-000000000001',
   'e0000000-0000-4000-8000-000000000001',
   1, NULL,
   '{"name":"Strahd obsesses over Ireena","fromEntityId":"ac000000-0000-4000-8000-000000000001","toEntityId":"ac000000-0000-4000-8000-000000000002","type":"obsession","description":"Strahd believes Ireena is the reincarnation of his long-dead love Tatyana and is determined to claim her as his own. He has visited her twice and bitten her on the neck."}',
   '2026-01-11 19:00:00+00'),

  ('a7000000-0000-4000-8000-000000000002',
   'a6000000-0000-4000-8000-000000000002',
   'e0000000-0000-4000-8000-000000000001',
   1, NULL,
   '{"name":"Ireena and Ismark are siblings","fromEntityId":"ac000000-0000-4000-8000-000000000002","toEntityId":"ac000000-0000-4000-8000-000000000003","type":"sibling","description":"Ireena and Ismark are adoptive siblings. Ismark is fiercely protective of Ireena and arranged for the party to escort her to safety."}',
   '2026-01-11 19:00:00+00');

-- =============================================================================
-- WORLD STATE — Lost Mine of Phandelver (session 1 only)
-- =============================================================================

INSERT INTO actors (id, campaign_id, owner_id, name, created_at, created_in_session_id) VALUES
  ('ac000000-0000-4000-8000-000000000010', 'c0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000001',
   'Gundren Rockseeker', '2026-01-15 20:00:00+00', 'e0000000-0000-4000-8000-000000000010'),
  ('ac000000-0000-4000-8000-000000000011', 'c0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000001',
   'Sildar Hallwinter',  '2026-01-15 20:00:00+00', 'e0000000-0000-4000-8000-000000000010');

INSERT INTO actor_versions
  (id, actor_id, session_id, version_number, changed_fields, full_snapshot, created_at)
VALUES
  ('a1000000-0000-4000-8000-000000000010',
   'ac000000-0000-4000-8000-000000000010',
   'e0000000-0000-4000-8000-000000000010',
   1, NULL,
   '{"name":"Gundren Rockseeker","description":"A dwarf prospector who hired the party to escort a supply wagon to Phandalin. He has a map to Wave Echo Cave and the legendary Forge of Spells, and has been captured by goblins and taken to Cragmaw Castle.","role":"Quest giver / captive","status":"Captured — location: Cragmaw Castle"}',
   '2026-01-15 20:00:00+00'),

  ('a1000000-0000-4000-8000-000000000011',
   'ac000000-0000-4000-8000-000000000011',
   'e0000000-0000-4000-8000-000000000010',
   1, NULL,
   '{"name":"Sildar Hallwinter","description":"A human warrior and member of the Lords'' Alliance. He was travelling with Gundren but was captured by goblins at the ambush. The party rescued him from Cragmaw Hideout. He seeks to re-establish order in Phandalin and locate the missing Lord''s Alliance agent Iarno Albrek.","role":"Ally / information source","status":"Alive — rescued by party"}',
   '2026-01-15 20:00:00+00');

INSERT INTO spaces (id, campaign_id, owner_id, name, created_at, created_in_session_id) VALUES
  ('a2000000-0000-4000-8000-000000000010', 'c0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000001',
   'Cragmaw Hideout', '2026-01-15 20:00:00+00', 'e0000000-0000-4000-8000-000000000010');

INSERT INTO space_versions
  (id, space_id, session_id, version_number, changed_fields, full_snapshot, created_at)
VALUES
  ('a3000000-0000-4000-8000-000000000010',
   'a2000000-0000-4000-8000-000000000010',
   'e0000000-0000-4000-8000-000000000010',
   1, NULL,
   '{"name":"Cragmaw Hideout","description":"A goblin-infested cave complex on the Triboar Trail used as a base by King Grol''s Cragmaw tribe. Sildar Hallwinter was imprisoned here. The party cleared the hideout and rescued Sildar.","type":"Cave/Dungeon","status":"Cleared by party"}',
   '2026-01-15 20:00:00+00');

INSERT INTO events (id, campaign_id, owner_id, name, created_at, created_in_session_id) VALUES
  ('a4000000-0000-4000-8000-000000000010', 'c0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000001',
   'Goblin ambush on the Triboar Trail', '2026-01-15 20:00:00+00', 'e0000000-0000-4000-8000-000000000010');

INSERT INTO event_versions
  (id, event_id, session_id, version_number, changed_fields, full_snapshot, created_at)
VALUES
  ('ee000000-0000-4000-8000-000000000010',
   'a4000000-0000-4000-8000-000000000010',
   'e0000000-0000-4000-8000-000000000010',
   1, NULL,
   '{"name":"Goblin ambush on the Triboar Trail","description":"The party discovered two dead horses and was ambushed by goblins on the Triboar Trail. They tracked the goblins back to Cragmaw Hideout, defeated the occupants, and rescued Sildar Hallwinter. Gundren Rockseeker and his map were taken to Cragmaw Castle.","outcome":"Sildar rescued; Gundren''s location identified; map is missing"}',
   '2026-01-15 20:00:00+00');

COMMIT;
