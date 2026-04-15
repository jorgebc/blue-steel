---
name: backend-domain-model
description: >
  Use this skill whenever you are creating or modifying a domain entity in `apps/api` —
  especially world state entities (Actor, Space, Event, Relation) that follow the versioned
  entity pattern. Triggers include: "add a new entity type", "add a field to an Actor/Space/Event",
  "model [domain concept]", "create the domain class for X", or any task that requires a new
  JPA table + domain object + versioning table. This skill covers the full chain: domain class →
  JPA entity → mapper → driven port → Liquibase migration. It encodes the world state versioning
  pattern that all four primary domain entities share.
---

# Backend — Adding or Modifying a Domain Entity

Blue Steel's domain model is split across two layers: pure Java domain objects in `domain/`
(zero framework imports) and JPA entities in `adapters/out/persistence/` (framework-annotated
persistence representation). They are always kept in sync via mappers. The world state entities
(Actor, Space, Event, Relation) follow an additional versioning pattern that is the direct
implementation of D-001, D-003, and D-035.

## Context

**Core principles:**

- Domain entities are plain Java — no `@Entity`, no `@Column`, no Spring annotations (ARCH-01).
  JPA entities live exclusively in `adapters.out.persistence` (ARCHITECTURE.md §3.5).
- Value objects are Java Records — immutable by construction.
- Aggregates enforce their own invariants — no anemic domain model.
- All domain entities carry `owner_id` and `campaign_id` from day one (D-021).
- All PKs are UUIDs. All tables use snake_case. Timestamps: `created_at`, `updated_at` (if
  mutable), domain-specific timestamps like `committed_at` (D-029, §5.7).
- Version history rows are **append-only** — no updates or deletes to existing version rows (D-001).
- Every world state fact is traceable to the session that produced it (D-003).

**World state versioning pattern (D-035):**

Each world state entity has two tables: a head record (`actors`) and an append-only version
history (`actor_versions`). Every version row stores:
- `changed_fields` JSONB — the delta only (what changed this session, D-006)
- `full_snapshot` JSONB — complete state at this version (for efficient point-in-time reads)
- `session_id` FK — traceability to the session that produced this version

Current state = row with max `version_number` per entity.
Point-in-time state = row with max `version_number` where `session_id ≤ target session`.

## Workflow

### 1. Define the domain entity class

Create the domain entity in `domain/<subdomain>/` (e.g., `domain/worldstate/actor/Actor.java`).

- Plain Java class — no framework annotations of any kind.
- For value objects (e.g., `ActorStatus`, `RelationType`): use a Java Record or sealed interface.
- Aggregates must enforce their invariants in their own methods:

```java
// domain/worldstate/actor/Actor.java
public class Actor {
    private final UUID id;
    private final UUID campaignId;
    private final UUID ownerId;           // D-021
    private final String name;
    private final List<ActorVersion> versionHistory;  // append-only

    // Enforce append-only invariant
    public Actor applyVersion(ActorVersion newVersion) {
        if (newVersion.versionNumber() <= currentVersionNumber()) {
            throw new DomainException("Version numbers must be monotonically increasing");
        }
        // return a new Actor with appended version — immutable update
    }

    public ActorVersion currentVersion() {
        return versionHistory.stream()
            .max(Comparator.comparingInt(ActorVersion::versionNumber))
            .orElseThrow();
    }
}
```

- For world state entities, model the version record as a nested Record:

```java
// domain/worldstate/actor/ActorVersion.java
public record ActorVersion(
    UUID id,
    UUID sessionId,    // traceability (D-003)
    int versionNumber,
    Map<String, Object> changedFields,  // delta (D-006)
    Map<String, Object> fullSnapshot    // complete state at this version
) {}
```

### 2. Write domain unit tests

Before implementing anything else, write pure JUnit 5 tests in `test/domain/<subdomain>/`:

- No Spring context, no Mockito (unless testing collaborators), no Testcontainers.
- Test all invariant-enforcement paths: valid construction, invalid transitions, edge cases.
- For world state aggregates, test that version history is append-only (append works,
  duplicate version number throws, history is preserved).

### 3. Create the Liquibase migration

See `database-migration` skill for the full workflow. For a new world state entity, you need
two changesets: the head table and the versions table.

**Head table template:**
```xml
<createTable tableName="actors">
    <column name="id" type="UUID"><constraints primaryKey="true"/></column>
    <column name="campaign_id" type="UUID"><constraints nullable="false" foreignKeyName="fk_actors_campaign" references="campaigns(id)"/></column>
    <column name="owner_id" type="UUID"><constraints nullable="false" foreignKeyName="fk_actors_owner" references="users(id)"/></column>
    <column name="name" type="TEXT"><constraints nullable="false"/></column>
    <column name="created_at" type="TIMESTAMP WITH TIME ZONE"><constraints nullable="false"/></column>
    <column name="created_in_session_id" type="UUID"><constraints nullable="false" foreignKeyName="fk_actors_created_session" references="sessions(id)"/></column>
</createTable>
```

**Versions table template:**
```xml
<createTable tableName="actor_versions">
    <column name="id" type="UUID"><constraints primaryKey="true"/></column>
    <column name="actor_id" type="UUID"><constraints nullable="false" foreignKeyName="fk_actor_versions_actor" references="actors(id)"/></column>
    <column name="session_id" type="UUID"><constraints nullable="false" foreignKeyName="fk_actor_versions_session" references="sessions(id)"/></column>
    <column name="version_number" type="INTEGER"><constraints nullable="false"/></column>
    <column name="changed_fields" type="JSONB"/>
    <column name="full_snapshot" type="JSONB"><constraints nullable="false"/></column>
    <column name="created_at" type="TIMESTAMP WITH TIME ZONE"><constraints nullable="false"/></column>
</createTable>
<addUniqueConstraint tableName="actor_versions" columnNames="actor_id, version_number"/>
```

### 4. Create the JPA entity (persistence representation)

Create JPA entity classes in `adapters/out/persistence/<subdomain>/`:

```java
// adapters/out/persistence/actor/ActorEntity.java
@Entity
@Table(name = "actors")
public class ActorEntity {
    @Id
    private UUID id;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_in_session_id", nullable = false)
    private UUID createdInSessionId;

    @OneToMany(mappedBy = "actor", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ActorVersionEntity> versions;
}
```

- JPA entities may use Lombok (`@Builder`, `@Getter`, `@Setter`) where Records are insufficient
  (e.g., when JPA requires a no-arg constructor with setters).
- JSONB columns (`changed_fields`, `full_snapshot`) use `String` or `Map<String, Object>` with
  a custom Hibernate type or Jackson converter.
- Never put business logic in JPA entities — they are a persistence detail.

### 5. Create the mapper

Create a mapper class in the same persistence adapter package:

```java
// adapters/out/persistence/actor/ActorMapper.java
@Component
public class ActorMapper {
    public Actor toDomain(ActorEntity entity) { ... }
    public ActorEntity toEntity(Actor domain) { ... }
    public ActorVersion toDomainVersion(ActorVersionEntity entity) { ... }
    public ActorVersionEntity toVersionEntity(ActorVersion domain, UUID actorId) { ... }
}
```

- Mappers translate between domain objects and JPA entities at the adapter boundary.
- Mappers are the only classes allowed to know about both sides.
- Never pass a JPA entity to the application layer or domain.

### 6. Create the Spring Data repository interface

```java
// adapters/out/persistence/actor/ActorJpaRepository.java
public interface ActorJpaRepository extends JpaRepository<ActorEntity, UUID> {
    List<ActorEntity> findByCampaignId(UUID campaignId);

    // Point-in-time query: latest version at or before a given session sequence number
    // D-035 — use native SQL for complex version queries
    @Query(nativeQuery = true, value = """
        SELECT av.* FROM actor_versions av
        JOIN sessions s ON s.id = av.session_id
        WHERE av.actor_id = :actorId
          AND s.sequence_number <= :sessionSequenceNumber
        ORDER BY av.version_number DESC
        LIMIT 1
        """)
    Optional<ActorVersionEntity> findVersionAtSession(UUID actorId, int sessionSequenceNumber);
}
```

### 7. Define the driven port interface

Create in `application/port/out/`:

```java
public interface ActorRepository {
    Optional<Actor> findById(UUID actorId);
    List<Actor> findByCampaignId(UUID campaignId);
    Actor save(Actor actor);
    void saveVersion(UUID actorId, ActorVersion version);
}
```

- The interface uses only domain types. No JPA entity types cross this boundary.

### 8. Implement the persistence adapter

Create `ActorPersistenceAdapter.java` implementing the driven port:

```java
// adapters/out/persistence/actor/ActorPersistenceAdapter.java
@Repository
public class ActorPersistenceAdapter implements ActorRepository {
    private final ActorJpaRepository jpaRepository;
    private final ActorMapper mapper;

    @Override
    public Actor save(Actor actor) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(actor)));
    }
}
```

### 9. Write persistence integration tests

Before using the adapter in any service, write Testcontainers-based integration tests in
`test/adapters/persistence/`:

- Use `@DataJpaTest` (lighter) or `@SpringBootTest` slice with Testcontainers PostgreSQL.
- Test that Liquibase migrations run cleanly against a real PostgreSQL container.
- Test that `save()` and version queries return the expected domain objects.
- Test the point-in-time version query specifically — it is a native SQL query that
  can silently regress.

## Patterns & Conventions

**The two-table versioning pattern applies to: Actor, Space, Event, Relation.**
Non-world-state entities (Campaign, Session, NarrativeBlock, Annotation, RefreshToken) do not
use this pattern.

**`owner_id` semantics (D-021):**
`owner_id` is the `user_id` of the GM or editor whose session commit introduced this entity to
world state. It is set at creation and never transferred. For Campaign, `owner_id` is the assigned
GM.

**Prohibited patterns:**
- JPA annotations in `domain/` — ArchUnit will fail the build.
- Business logic in JPA entities — they are persistence representations only.
- Mutable version history — `actor_versions` rows are never updated or deleted.
- Nullable columns without documented justification.

## Examples

**Domain entity with a field addition (adding `status` to Actor):**

1. Add `status` field to `Actor` domain class (Java 25 sealed interface or enum as nested type).
2. Add corresponding column to an **existing** changeset? No — create a new changeset
   (`0015_add_actor_status.xml`) with `addColumn`. See `database-migration` skill.
3. Add `status` column to `ActorEntity` JPA class.
4. Update `ActorMapper` to map the new field in both directions.
5. Update affected unit tests and integration tests.

## Common Pitfalls

- **Modifying an applied Liquibase changeset.** Never edit a changeset that has already been
  applied in any environment. Add a new changeset instead (DB-02).

- **Forgetting `campaign_id` or `owner_id` on a new entity (D-021).** Every domain entity
  needs both from day one. Retrofitting them later requires a data migration.

- **Using `MAX(version_number)` without a `campaign_id` or `actor_id` scope.** Version numbers
  are per-entity, not global. Always scope version queries to a specific entity.

- **Loading full version history eagerly.** Use `FetchType.LAZY` for version history collections.
  Loading all versions on every actor read is a performance trap.

- **Storing raw JPA entity in `changed_fields` / `full_snapshot`.** These JSONB columns store
  serialised domain state — the mapper must serialise the domain object, not the JPA entity.

## References

- `apps/api/CLAUDE.md` §6 "Backend Domain Concepts"
- `ARCHITECTURE.md` §5.4 (world state versioning schema), §3.5 (domain model conventions)
- `DECISIONS.md` D-001, D-003, D-021, D-035
- `database-migration` skill (for Liquibase changeset creation)
- `backend-testing` skill (for Testcontainers integration test setup)
