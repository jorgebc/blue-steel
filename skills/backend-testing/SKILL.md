---
name: backend-testing
description: >
  Use this skill whenever you are writing, running, or debugging tests in `apps/api`. Triggers
  include: "write a test for X", "run the tests", "add a test case", "set up integration tests",
  "run mutation testing", "add an ArchUnit rule", or any task involving JUnit, Mockito,
  Testcontainers, ArchUnit, or PITest. This skill describes the four-tier test strategy for
  Blue Steel's backend, what belongs in each tier, and how to run them correctly. TDD is the
  required development process — this skill should be consulted before writing production code.
---

# Backend — Test Strategy

Blue Steel uses a four-tier backend testing strategy: domain unit tests, application unit tests,
persistence integration tests, and architecture enforcement tests. Each tier has a specific scope,
toolset, and performance budget. Mutation testing (PITest) runs on the domain core. There are no
E2E tests — the highest confidence tier is integration tests with external services mocked (D-056).

TDD is not optional (Kent Beck). Write a failing test before writing production code. This applies
to all four tiers.

## Context

**Relevant decisions:**
- D-036: PITest mutation testing via Maven plugin — domain core mandatory on every build
- D-037: ArchUnit boundary enforcement — runs on every build
- D-056: No E2E tests; backend top tier is integration tests with Testcontainers
- ARCH-01: Domain core has zero framework imports — ArchUnit enforces this
- TEST-01: Every domain class needs unit tests; every use-case service needs unit tests with
  mocked ports; persistence adapters require Testcontainers integration tests

**Test directory structure:**

```
apps/api/src/test/java/com/bluesteel/
├── architecture/      ← ArchUnit rules (all layer boundary enforcement)
├── domain/            ← Pure unit tests for domain classes and aggregates
├── application/       ← Unit tests for use-case services (all ports mocked)
└── adapters/
    ├── in/            ← Controller slice tests (optional; thin controllers may not need them)
    └── out/
        ├── persistence/ ← Integration tests: real PostgreSQL via Testcontainers
        └── ai/          ← Unit tests for AI adapter mapping logic (LLM calls mocked)
```

## The Four Tiers

### Tier 1 — Domain Unit Tests

**What:** Test a single domain class (entity, aggregate, value object, domain service) in complete
isolation. No Spring context. No Testcontainers. No Mockito unless testing a collaborator.

**Where:** `test/domain/<subdomain>/`

**Tools:** JUnit 5 only (or JUnit 5 + Mockito for collaborator scenarios).

**What to cover:**
- All aggregate invariant enforcement (valid construction, invalid transitions, boundary cases)
- Domain service logic
- Value object equality and construction rules
- State machine transitions (e.g., Session lifecycle: `pending → processing → draft → committed`)

**Example:**

```java
@Test
void sessionCannotBeCommittedFromFailedStatus() {
    Session session = Session.create(campaignId, ownerId, 1);
    session.transitionTo(SessionStatus.FAILED);

    assertThrows(InvalidSessionTransitionException.class,
        () -> session.transitionTo(SessionStatus.COMMITTED));
}
```

**PITest scope:** Domain core is the primary PITest target. Every public method in `domain/`
must be covered to the configured minimum threshold. Run PITest explicitly when modifying
domain logic:

```bash
# [Command TBD before Phase 1 — see apps/api/CLAUDE.md §4]
mvn test -pl apps/api pitest:mutationCoverage -Dpitest.targetClasses=com.bluesteel.domain.*
```

### Tier 2 — Application Layer Unit Tests

**What:** Test a use-case service class in isolation. All driven ports are mocked with Mockito.
No Spring context. No Testcontainers.

**Where:** `test/application/<domain>/`

**Tools:** JUnit 5 + Mockito.

**What to cover:**
- Orchestration logic: does the service call the right ports in the right order?
- Authorization enforcement: does the service reject callers with insufficient role?
- Business rule violations: does the service throw the right domain exception for invalid inputs?
- Output mapping: does the service return the correct result from mocked port responses?

**Example:**

```java
@ExtendWith(MockitoExtension.class)
class CommitSessionServiceTest {

    @Mock SessionRepository sessionRepository;
    @Mock ActorRepository actorRepository;
    @Mock CampaignMembershipPort membershipPort;

    @InjectMocks CommitSessionService sut;

    @Test
    void rejectsCommitWithUncertainEntities() {
        // arrange: session in DRAFT status, commit payload contains UNCERTAIN entities
        when(sessionRepository.findById(any())).thenReturn(Optional.of(draftSession()));
        when(membershipPort.resolveRole(any(), any())).thenReturn(CampaignRole.GM);
        CommitPayload payload = payloadWithUncertainEntities();

        // act + assert
        assertThrows(UncertainEntitiesPresentException.class,
            () -> sut.commit(campaignId, sessionId, callerId, payload));
    }
}
```

**Never use `@SpringBootTest` here.** The full Spring context is orders of magnitude slower
and does not add test value for application layer logic.

### Tier 3 — Persistence Integration Tests

**What:** Test Spring Data JPA repositories and native pgvector queries against a real PostgreSQL
database. Validates that Liquibase migrations run cleanly, that JPA mappings are correct, and
that native SQL queries produce the expected results.

**Where:** `test/adapters/out/persistence/`

**Tools:** JUnit 5 + Testcontainers (`@TestContainers`, `@Container PostgreSQLContainer`) +
Spring Data JPA test slice (`@DataJpaTest` or `@SpringBootTest` with Testcontainers).

**What to cover:**
- Liquibase migration correctness: container spins up and all changelogs apply without error
- CRUD operations: save, find, update
- Native queries: pgvector similarity search, point-in-time version queries, partial unique index
  enforcement
- Constraint violations: duplicate GM per campaign, duplicate sequence_number per campaign

**Testcontainers setup:**

```java
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ActorPersistenceAdapterTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
        .withDatabaseName("bluesteel_test");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
```

**Note:** Use the `pgvector/pgvector:pg16` Docker image (not plain PostgreSQL) — it includes the
pgvector extension required for similarity search columns and queries.

**Important gotcha — embedding generation is async (D-063):** Tests that exercise the query
pipeline after a commit must either await embedding completion or mock the `EmbeddingPort`.
Do not assert query results synchronously after a commit without accounting for this.

### Tier 4 — ArchUnit Architecture Tests

**What:** Enforce Hexagonal Architecture layer boundaries. Run on every build. Fast (no external
services).

**Where:** `test/architecture/ArchitectureTest.java` — a single class containing all rules.

**Tools:** ArchUnit.

**Existing rules to maintain:**

```java
// Rule: Domain core has zero framework imports (ARCH-01)
noClasses().that().resideInAPackage("..domain..")
    .should().dependOnClassesThat()
    .resideInAnyPackage("org.springframework..", "jakarta.persistence..")
    .check(classes);

// Rule: Adapters depend on application layer; domain never depends on adapters (ARCH-02)
noClasses().that().resideInAPackage("..domain..")
    .should().dependOnClassesThat()
    .resideInAPackage("..adapters..")
    .check(classes);

// Rule: Application layer does not depend on adapters
noClasses().that().resideInAPackage("..application..")
    .should().dependOnClassesThat()
    .resideInAPackage("..adapters..")
    .check(classes);
```

**Adding a new ArchUnit rule:** Add it to the existing `ArchitectureTest` class. Do not create
a separate test class per rule.

**A failing ArchUnit test is a layer violation, not a test to fix.** If ArchUnit flags a new
class, fix the class — do not relax the ArchUnit rule.

## Running Tests

> ⚠️ Exact commands to be filled in before Phase 1 (see `apps/api/CLAUDE.md` §4). Outlines below.

```bash
# Fast unit tests + ArchUnit (no Docker required)
mvn test -pl apps/api

# All tests including integration (Docker required for Testcontainers)
mvn verify -pl apps/api

# Domain core mutation tests (slow — run deliberately, not on every save)
mvn test pitest:mutationCoverage -pl apps/api \
  -Dpitest.targetClasses="com.bluesteel.domain.*"

# Application layer mutation tests (pre-merge)
mvn test pitest:mutationCoverage -pl apps/api \
  -Dpitest.targetClasses="com.bluesteel.application.*"

# ArchUnit only (very fast)
mvn test -pl apps/api -Dtest=ArchitectureTest
```

## Patterns & Conventions

**Test class naming:** `<ClassUnderTest>Test.java` for unit tests;
`<AdapterName>IntegrationTest.java` for integration tests.

**Test method naming:** Descriptive, sentence-style, in camelCase or backtick style:
`rejectsCommitWithUncertainEntities`, `findsActorAtPointInTime`.

**Arrange-Act-Assert:** Use comments `// arrange`, `// act`, `// assert` for tests with non-trivial
setup.

**Mock discipline:** In application layer tests, mock ALL driven ports — even those not called by
the path under test. Use `verify(port, never())` assertions to confirm ports that should NOT be
called. This documents intent and catches accidental calls.

**Test data builders:** Create inner static builder helpers or test factory classes for domain
objects used across multiple test cases. Avoid duplicating construction logic across test classes.

## Common Pitfalls

- **Using `@SpringBootTest` for domain or application layer tests.** This spins up the full
  Spring context, loads all beans, and connects to the database. It is wrong for unit-level tests.
  Use `@ExtendWith(MockitoExtension.class)` for application tests.

- **Not using the pgvector PostgreSQL image in Testcontainers.** Plain `postgres:16` does not
  include the pgvector extension. The `entity_embeddings` table migration will fail.

- **Testing the happy path only.** Domain invariants derive their value from the unhappy paths.
  Test every exception branch in aggregate methods.

- **Asserting query results synchronously after a commit.** Embedding generation is async
  (D-063). Entity versions without embeddings are excluded from Query Mode retrieval. Mock
  `EmbeddingPort` in tests that exercise query retrieval after a commit.

- **Modifying an ArchUnit rule to make it pass.** A failing ArchUnit test means production code
  violated the architecture. Fix the production code.

- **Running PITest as part of normal development loop.** PITest is slow. Run it deliberately
  when modifying domain logic, not on every save. CI runs it automatically.

## References

- `apps/api/CLAUDE.md` §7 TEST-01 convention
- `DECISIONS.md` D-036 (PITest), D-037 (ArchUnit), D-056 (no E2E)
- `ARCHITECTURE.md` §3.2 (hexagonal architecture rules)
- `backend-domain-model` skill (domain entity test examples)
- `backend-endpoint` skill (application service test examples)
