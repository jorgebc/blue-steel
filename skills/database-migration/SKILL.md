---
name: database-migration
description: >
  Use this skill whenever you need to add, modify, or validate a database schema change in
  `apps/api`. Triggers include: "add a table", "add a column", "create an index", "database
  migration", "Liquibase changeset", "schema change", "add FK", "add a constraint",
  "pgvector column", "validate migration", or any task that requires writing to
  `apps/api/src/main/resources/db/changelog/`. This skill is the single reference for all
  Liquibase changeset creation, naming conventions, schema invariants, and Neon branch validation.
---

# Database — Adding a Liquibase Migration

All schema changes in Blue Steel are managed through Liquibase. Migration files are append-only
— a changeset that has been applied to any environment must never be modified. All new schema work
creates new changesets. The database uses PostgreSQL with the pgvector extension.

## Context

**Relevant decisions:**
- D-029: Liquibase is the migration tool; rollback is built-in
- DB-01: Native SQL for all pgvector queries; standard CRUD via Spring Data JPA
- DB-02: Liquibase changelogs are append-only after application
- D-047: Neon free tier; use Neon database branching to validate migrations before applying to prod
- D-021: All domain entities carry `owner_id` and `campaign_id`
- ARCHITECTURE.md §5.7: Schema conventions (UUID PKs, snake_case, created_at, explicit FKs)

**File locations:**

```
apps/api/src/main/resources/db/changelog/
├── db.changelog-master.xml     ← root changelog; includes all versioned files
├── 0001_create_users.xml
├── 0002_create_campaigns.xml
├── 0003_create_campaign_members.xml
├── ...
└── NNNN_description.xml        ← new changesets follow this naming pattern
```

**Schema invariants (ARCHITECTURE.md §5.7):**
- All PKs are UUIDs — no auto-increment integers
- All tables have `created_at TIMESTAMP WITH TIME ZONE NOT NULL`
- Mutable tables also have `updated_at TIMESTAMP WITH TIME ZONE`
- All domain entities carry `campaign_id` and `owner_id` (D-021)
- All FKs are explicitly declared (no implicit FK via naming only)
- No nullable columns without documented justification
- Table names: plural nouns, snake_case (e.g., `actor_versions`, `entity_embeddings`)
- Column names: snake_case
- FK column naming: `{referenced_table_singular}_id` (e.g., `campaign_id`, `session_id`)

## Workflow

### 1. Determine the sequence number

Check the last numbered file in `db/changelog/` and use the next number:

```bash
ls apps/api/src/main/resources/db/changelog/ | sort | tail -5
# e.g., last is 0014_create_proposals.xml → new file is 0015_...
```

### 2. Create the changeset file

**File naming:** `NNNN_<short_description>.xml` where NNNN is zero-padded (0015, 0016, ...).
Keep descriptions short and imperative: `0015_add_actor_status_column`, `0016_create_annotations`.

**Changeset XML template:**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd">

    <changeSet id="0015-add-actor-status" author="jorge">

        <addColumn tableName="actors">
            <column name="status" type="TEXT">
                <constraints nullable="false" defaultValue="active"/>
            </column>
        </addColumn>

    </changeSet>
</databaseChangeLog>
```

**Changeset ID convention:** `NNNN-description` (matches filename without extension).
**Author:** use a consistent identifier (developer name or GitHub handle).

### 3. Include it in the master changelog

```xml
<!-- db.changelog-master.xml -->
<include file="db/changelog/0015_add_actor_status_column.xml"
         relativeToChangelogFile="false"/>
```

Always append at the bottom — changelogs are applied in inclusion order.

### 4. Common changeset patterns

**Creating a new table:**

```xml
<createTable tableName="annotations">
    <column name="id" type="UUID">
        <constraints primaryKey="true" nullable="false"/>
    </column>
    <column name="campaign_id" type="UUID">
        <constraints nullable="false"
                     foreignKeyName="fk_annotations_campaign"
                     references="campaigns(id)"/>
    </column>
    <column name="entity_type" type="TEXT">
        <constraints nullable="false"/>
    </column>
    <column name="entity_id" type="UUID">
        <constraints nullable="false"/>
    </column>
    <column name="author_id" type="UUID">
        <constraints nullable="false"
                     foreignKeyName="fk_annotations_author"
                     references="users(id)"/>
    </column>
    <column name="content" type="TEXT">
        <constraints nullable="false"/>
    </column>
    <column name="created_at" type="TIMESTAMP WITH TIME ZONE">
        <constraints nullable="false"/>
    </column>
    <!-- No updated_at: annotations are immutable after creation (D-011) -->
</createTable>
```

**Adding a column to an existing table:**

```xml
<addColumn tableName="sessions">
    <column name="failure_reason" type="TEXT">
        <!-- Nullable: only populated when status = 'failed' -->
        <constraints nullable="true"/>
    </column>
</addColumn>
```

**Creating an index:**

```xml
<createIndex tableName="actor_versions" indexName="idx_actor_versions_actor_id">
    <column name="actor_id"/>
</createIndex>
```

**Creating a partial unique index** (e.g., enforce singleton admin, D-025):

```xml
<!-- Partial unique index: only one user may have is_admin = TRUE -->
<sql>
    CREATE UNIQUE INDEX uidx_users_singleton_admin
    ON users (is_admin)
    WHERE is_admin = TRUE;
</sql>
```

**pgvector column:**

```xml
<!-- First changeset: enable the extension (once, in the first migration that needs it) -->
<sql>CREATE EXTENSION IF NOT EXISTS vector;</sql>

<!-- Column definition for embedding storage -->
<addColumn tableName="entity_embeddings">
    <column name="embedding" type="vector(1536)">
        <!-- 1536 dimensions for text-embedding-3-small (D-040) -->
        <constraints nullable="false"/>
    </column>
</addColumn>
```

**JSONB column:**

```xml
<addColumn tableName="sessions">
    <column name="diff_payload" type="JSONB">
        <constraints nullable="true"/>
    </column>
</addColumn>
```

**Adding a rollback clause** (required for non-trivial changes; automatically supported for
standard Liquibase commands like `addColumn`, `createTable`):

```xml
<changeSet id="0016-add-session-sequence" author="jorge">
    <addColumn tableName="sessions">
        <column name="sequence_number" type="INTEGER">
            <constraints nullable="false"/>
        </column>
    </addColumn>
    <rollback>
        <dropColumn tableName="sessions" columnName="sequence_number"/>
    </rollback>
</changeSet>
```

### 5. Validate against a Neon branch (DB-02, D-047)

Before applying to production:

1. In the Neon console, branch the production database to create an isolated validation branch.
2. Apply the new changeset against the Neon branch:
   - Update `DATABASE_URL` in `.env.local` to point to the Neon branch connection string.
   - Run the application or Liquibase directly — it will apply pending changesets.
3. Verify the schema diff in the Neon console.
4. Run the Testcontainers integration tests against the Neon branch (optional but recommended for
   complex migrations).
5. If validated: apply to production by pushing the changeset to `main` (CI applies it on deploy).
6. Delete the Neon validation branch after confirmation.

> ⚠️ Neon branch management commands to be documented when Neon is provisioned.

### 6. Verify locally with Testcontainers

The persistence integration tests (Testcontainers) apply all Liquibase migrations against a fresh
PostgreSQL container on each test run. If a migration breaks, integration tests fail.

```bash
# Run integration tests — also validates all migrations
mvn verify -pl apps/api
```

A migration error appears as: `liquibase.exception.MigrationFailedException` in the test output.

## World State Versioning Schema Pattern

For any new world state entity that follows the versioned pattern (D-035), create two changesets:
one for the head table, one for the versions table.

See `backend-domain-model` skill for the schema template.

## Checklist Before Committing a New Changeset

- [ ] File named `NNNN_<description>.xml` with correct sequence number
- [ ] Included in `db.changelog-master.xml`
- [ ] Changeset ID matches filename
- [ ] All PKs are UUIDs
- [ ] `created_at` column present on new tables (and `updated_at` if mutable)
- [ ] Domain entity tables have `campaign_id` and `owner_id` (D-021)
- [ ] All FKs are explicitly declared
- [ ] No nullable columns without comment explaining why
- [ ] pgvector extension created before first vector column
- [ ] Rollback clause present for non-standard operations (`<sql>`, `<createProcedure>`, etc.)
- [ ] Validated against Neon branch before production deploy

## Common Pitfalls

- **Modifying an applied changeset.** This is the most severe mistake — Liquibase tracks applied
  changelogs by their MD5 checksum. A modified applied changeset causes a fatal checksum error on
  next startup. If this happens in production, it requires manual intervention. Always add a new
  changeset (DB-02).

- **Forgetting to include the file in `db.changelog-master.xml`.** The changeset file exists but
  will never be applied. No error appears at startup — it is silently ignored.

- **Using `INTEGER SERIAL` or `BIGSERIAL` as PKs.** Blue Steel uses UUIDs for all PKs
  (ARCHITECTURE.md §5.7). Auto-increment integers are not used.

- **Creating pgvector columns before enabling the extension.** `CREATE EXTENSION IF NOT EXISTS vector`
  must appear in a prior changeset before any `vector(1536)` column is created.

- **Using the wrong dimension count for embeddings.** Blue Steel uses `text-embedding-3-small`
  with 1536 dimensions (D-040). The column type must be `vector(1536)`. Using a different
  dimension count silently corrupts stored embeddings.

- **Creating a partial unique index with Liquibase `<createIndex>`.** Liquibase's standard
  `<createIndex>` element does not support `WHERE` clauses. Use `<sql>` with a raw
  `CREATE UNIQUE INDEX ... WHERE ...` statement for partial indexes.

- **Not adding `campaign_id` and `owner_id` to a new domain entity table (D-021).** These are
  required from day one on all domain entities. Retrofitting them later requires a data migration.

## References

- `ARCHITECTURE.md` §5 (full database schema), §5.7 (schema conventions)
- `apps/api/CLAUDE.md` §7 DB-01, DB-02
- `apps/api/CLAUDE.md` §9 "Adding a new database model and migration"
- `DECISIONS.md` D-021, D-029, D-031, D-035, D-040, D-047, D-062
- `backend-domain-model` skill (domain entity + versioning schema pattern)
