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
    ├── in/            ← Controller slice tests (@WebMvcTest) — required when controllers
    │                    apply @Valid, map non-trivial HTTP statuses, or serialize complex shapes
    └── out/
        ├── persistence/ ← Integration tests: real PostgreSQL via Testcontainers
        └── ai/          ← Unit tests for AI adapter mapping logic (LLM calls mocked)
```

## Assertion Library

**Always use AssertJ for assertions.** AssertJ is on the classpath via `spring-boot-starter-test`
and produces significantly richer failure messages than JUnit's native assertions.

```java
// ✅ Preferred — AssertJ fluent assertions
assertThat(actor.getName()).isEqualTo("Aldric");
assertThat(session.getStatus()).isEqualTo(SessionStatus.DRAFT);
assertThat(result.getActors()).hasSize(3).extracting(Actor::getName).contains("Aldric");

// ✅ Also fine — JUnit assertThrows for exception testing
assertThrows(InvalidSessionTransitionException.class,
    () -> session.transitionTo(SessionStatus.COMMITTED));

// ❌ Avoid — JUnit native assertEquals produces unhelpful failure messages
assertEquals(SessionStatus.DRAFT, session.getStatus());
```

Import: `import static org.assertj.core.api.Assertions.assertThat;`

## The Four Tiers

### Tier 1 — Domain Unit Tests

**What:** Test a single domain class (entity, aggregate, value object, domain service) in complete
isolation. No Spring context. No Testcontainers.

**Mockito in Tier 1:** Domain objects should be tested with real instances wherever possible.
Use Mockito only when the class under test has a collaborator whose real construction is genuinely
expensive or pulls in infrastructure. **Do not mock other domain objects** — mock a value object
or entity and you are testing Mockito, not your domain. If setting up a collaborator feels
expensive, that is a design signal worth questioning.

**Where:** `test/domain/<subdomain>/`

**Tools:** JUnit 5 + AssertJ. Mockito only for genuine collaborator scenarios (see above).

**What to cover:**
- All aggregate invariant enforcement (valid construction, invalid transitions, boundary cases)
- Domain service logic
- Value object equality and construction rules
- State machine transitions (e.g., Session lifecycle: `pending → processing → draft → committed`)
- Every exception branch — domain invariants derive their value from unhappy paths

**Use `@DisplayName` on classes and methods.** Tests are living documentation. A report showing
`"Session aggregate > cannot transition from FAILED to COMMITTED"` is worth more than
`sessionCannotBeCommittedFromFailedStatus`.

**Use `@Nested` to group by initial state or scenario.** For aggregates with multiple lifecycle
states, flat test methods become unreadable. Group by precondition:

```java
@DisplayName("Session aggregate")
class SessionTest {

    @Nested
    @DisplayName("when in FAILED status")
    class WhenFailed {

        private Session session;

        @BeforeEach
        void setUp() {
            session = Session.create(campaignId(), ownerId(), 1);
            session.transitionTo(SessionStatus.FAILED);
        }

        @Test
        @DisplayName("cannot transition to COMMITTED")
        void cannotTransitionToCommitted() {
            assertThrows(InvalidSessionTransitionException.class,
                () -> session.transitionTo(SessionStatus.COMMITTED));
        }

        @Test
        @DisplayName("cannot transition to DRAFT")
        void cannotTransitionToDraft() {
            assertThrows(InvalidSessionTransitionException.class,
                () -> session.transitionTo(SessionStatus.DRAFT));
        }
    }

    @Nested
    @DisplayName("when in DRAFT status")
    class WhenDraft {

        @Test
        @DisplayName("transitions to COMMITTED successfully")
        void transitionsToCommitted() {
            Session session = Session.create(campaignId(), ownerId(), 1);
            session.transitionTo(SessionStatus.PROCESSING);
            session.transitionTo(SessionStatus.DRAFT);

            session.transitionTo(SessionStatus.COMMITTED);

            assertThat(session.getStatus()).isEqualTo(SessionStatus.COMMITTED);
        }
    }
}
```

**PITest scope:** Domain core is the primary PITest target. Every public method in `domain/`
must be covered to the configured minimum threshold. Run PITest explicitly when modifying
domain logic:

```bash
# Run from repo root — scoped to domain core (fast-ish; not part of normal dev loop)
mvn test-compile pitest:mutationCoverage -pl apps/api \
  -Dpitest.targetClasses="com.bluesteel.domain.*"
```

**Interpreting surviving mutants:** A surviving mutant means PITest introduced a code change
that no test caught. Work through this decision tree:

1. **Does the mutant expose a missing assertion?** Write the test. This is the most common case.
2. **Does the mutant expose a missing test scenario** (e.g., a boundary case you didn't consider)?
   Add the test case.
3. **Is the mutant equivalent** — the mutation produces identical observable behaviour
   (e.g., mutating `i++` to `i--` inside dead code, or swapping two constants that happen to be
   equal)? Mark it with an inline `//noinspection` comment and add a PITest `excludedMethods`
   entry in `pom.xml` with a justification comment.
4. **Is the surviving mutant in generated or trivial code** (getters, equals/hashCode)? Narrow the
   `targetClasses` or `excludedMethods` in `pom.xml` — do not write tests for generated code.

Do not raise the `mutationThreshold` to make a build green. A threshold reduction without a
written justification in `DECISIONS.md` is a red flag.

### Tier 2 — Application Layer Unit Tests

**What:** Test a use-case service class in isolation. All driven ports are mocked with Mockito.
No Spring context. No Testcontainers.

**Where:** `test/application/<domain>/`

**Tools:** JUnit 5 + Mockito + AssertJ.

**What to cover:**
- Orchestration logic: does the service call the right ports in the right order?
- Authorization enforcement: does the service reject callers with insufficient role?
- Business rule violations: does the service throw the right domain exception for invalid inputs?
- Output mapping: does the service return the correct result from mocked port responses?

**Use constructor injection, not `@InjectMocks`.** Mockito's `@InjectMocks` fails silently when
injection does not match (field injection, setter injection, and constructor injection are tried in
order; failure produces no error). With hexagonal architecture, services use constructor injection
by design — construct the service explicitly in `@BeforeEach`. This fails loudly at compile time
when a new dependency is added and forces you to update the test setup.

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("CommitSessionService")
class CommitSessionServiceTest {

    @Mock SessionRepository sessionRepository;
    @Mock ActorRepository actorRepository;
    @Mock CampaignMembershipPort membershipPort;

    CommitSessionService sut;

    @BeforeEach
    void setUp() {
        // ✅ Explicit constructor — compile error if a new dependency is added
        sut = new CommitSessionService(sessionRepository, actorRepository, membershipPort);
    }

    @Test
    @DisplayName("rejects commit when payload contains UNCERTAIN entities")
    void rejectsCommitWithUncertainEntities() {
        // arrange
        when(sessionRepository.findById(any())).thenReturn(Optional.of(draftSession()));
        when(membershipPort.resolveRole(any(), any())).thenReturn(CampaignRole.GM);
        CommitPayload payload = payloadWithUncertainEntities();

        // act + assert
        assertThrows(UncertainEntitiesPresentException.class,
            () -> sut.commit(campaignId, sessionId, callerId, payload));
    }

    @Test
    @DisplayName("does not touch ActorRepository when session is not in DRAFT status")
    void doesNotTouchActorRepositoryForNonDraftSession() {
        // arrange
        when(sessionRepository.findById(any())).thenReturn(Optional.of(committedSession()));
        when(membershipPort.resolveRole(any(), any())).thenReturn(CampaignRole.GM);

        // act + assert
        assertThrows(InvalidSessionStatusException.class,
            () -> sut.commit(campaignId, sessionId, callerId, validPayload()));

        verify(actorRepository, never()).save(any());
    }
}
```

**Mock discipline:** Mock all driven ports the service declares as dependencies. Use
`verify(port, never())` to assert ports that must not be called — this documents intent and
catches accidental calls. If you find yourself mocking more than 4–5 ports for a single service,
treat that as an SRP signal: the service may be doing too much.

**Never use `@SpringBootTest` here.** The full Spring context is orders of magnitude slower
and does not add test value for application layer logic.

### Tier 3 — Persistence Integration Tests

**What:** Test Spring Data JPA repositories and native pgvector queries against a real PostgreSQL
database. Validates that Liquibase migrations run cleanly, that JPA mappings are correct, and
that native SQL queries produce the expected results.

**Where:** `test/adapters/out/persistence/`

**Tools:** JUnit 5 + Testcontainers (`@Testcontainers`, `@Container PostgreSQLContainer`) +
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
@DisplayName("ActorPersistenceAdapter")
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

**Test isolation between test methods:** Without explicit cleanup, tests that persist data share
state within a single test run. Choose one strategy per test class and apply it consistently:

- **`@Transactional` on the test class** (simplest): Spring rolls back after each test method
  automatically. Suitable for most CRUD tests. **Caveat:** cannot test commit semantics or
  behaviour that only becomes visible after a transaction commits (e.g., constraint violations
  raised at flush time, async listeners triggered on commit).
- **`@Sql` cleanup script** (explicit): annotate each test or the class with
  `@Sql(scripts = "classpath:db/truncate.sql", executionPhase = BEFORE_EACH_TEST_METHOD)`.
  Works correctly for commit-level tests. Requires a `truncate.sql` in `test/resources/db/`.
- **Unique IDs per test** (lightweight): generate fresh UUIDs for each test's root entities so
  tests never collide. Leaves residue in the container but avoids truncation overhead. Acceptable
  for read-only tests or when residue does not affect assertions.

The default for persistence tests in this project is `@Transactional` unless the test explicitly
requires commit semantics — in which case use `@Sql` cleanup.

**Important gotcha — embedding generation is async (D-063):** Tests that exercise the query
pipeline after a commit must either await embedding completion or mock the `EmbeddingPort`.
Do not assert query results synchronously after a commit without accounting for this.

### Tier 3b — Controller Slice Tests

**What:** Test REST controller behaviour in isolation using `@WebMvcTest` + `MockMvc`. The driving
port (use-case interface) is mocked. No persistence, no Testcontainers.

**Where:** `test/adapters/in/`

**Tools:** JUnit 5 + `@WebMvcTest` + `MockMvc` + Mockito + AssertJ.

**When to write controller slice tests — use this decision rule:** Write a `@WebMvcTest` when
the controller does any of the following:
- Applies `@Valid` on a request body (test that 400 is returned with the correct error envelope)
- Maps a domain exception to a non-obvious HTTP status via `GlobalExceptionHandler`
- Serializes a complex response shape (nested objects, UUID/timestamp formatting)
- Applies security constraints (`@PreAuthorize`, `@Secured`)

Even a "thin" controller has a serialization contract. At minimum, every controller should have
one test verifying the `400` error envelope for a validation failure.

```java
@WebMvcTest(SessionController.class)
@DisplayName("SessionController")
class SessionControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean SubmitSessionUseCase submitSessionUseCase;

    @Test
    @DisplayName("returns 422 with UNCERTAIN_ENTITIES_PRESENT when commit payload is invalid")
    void returns422ForUncertainEntities() throws Exception {
        when(submitSessionUseCase.commit(any(), any(), any(), any()))
            .thenThrow(new UncertainEntitiesPresentException());

        mockMvc.perform(post("/api/v1/campaigns/{id}/sessions/{sid}/commit", campaignId, sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCommitPayloadJson()))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errors[0].code").value("UNCERTAIN_ENTITIES_PRESENT"));
    }

    @Test
    @DisplayName("returns 400 with field errors when request body fails validation")
    void returns400ForInvalidRequestBody() throws Exception {
        mockMvc.perform(post("/api/v1/campaigns/{id}/sessions/{sid}/commit", campaignId, sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))  // missing required fields
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors").isArray())
            .andExpect(jsonPath("$.errors[0].field").isNotEmpty());
    }
}
```

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

All commands run from the **repo root**. The `-pl apps/api` flag scopes Maven to the backend module.

```bash
# Fast: unit tests + ArchUnit (no Docker required, ~seconds)
mvn test -pl apps/api

# Full: unit + integration tests (Docker required for Testcontainers, ~minutes)
mvn verify -pl apps/api

# ArchUnit only (very fast — no Spring context)
mvn test -pl apps/api -Dtest=ArchitectureTest

# Domain core mutation tests (slow — run deliberately when modifying domain logic)
# Default PITest scope in pom.xml covers domain core only; this matches the CI default.
mvn test-compile pitest:mutationCoverage -pl apps/api \
  -Dpitest.targetClasses="com.bluesteel.domain.*"

# Application layer mutation tests (pre-merge, run explicitly — not part of default CI)
mvn test-compile pitest:mutationCoverage -pl apps/api \
  -Dpitest.targetClasses="com.bluesteel.application.*"
```

**PITest scope in CI:** The `pom.xml` plugin configuration scopes the default PITest run to
`com.bluesteel.domain.*` (D-036). This is what CI executes automatically. The application layer
command above uses an explicit `-DtargetClasses` override — it is **not** part of the standard CI
run. Run it manually before merging changes to the application layer.

## Patterns & Conventions

**Test class naming:** `<ClassUnderTest>Test.java` for unit tests;
`<AdapterName>IntegrationTest.java` for integration tests;
`<ControllerName>ControllerTest.java` for controller slice tests.

**`@DisplayName` on every test class and test method.** Classes use the domain concept name
(`@DisplayName("Session aggregate")`). Methods use natural-language sentences in the imperative
or descriptive mood (`@DisplayName("rejects commit when payload contains UNCERTAIN entities")`).
This turns the test report into readable documentation.

**Test method naming:** Descriptive, sentence-style camelCase matching the `@DisplayName` content:
`rejectsCommitWithUncertainEntities`, `findsActorAtPointInTime`.

**Arrange-Act-Assert:** Use `// arrange`, `// act`, `// assert` comments in tests with non-trivial
setup. Single-expression tests do not need comments.

**AssertJ for all assertions.** Use `assertThat(...)` for value, collection, and exception
message assertions. Use JUnit's `assertThrows` for exception type assertions only.

**Mock discipline:** In application layer tests, mock all driven ports the service depends on —
even those not called by the path under test. Use `verify(port, never())` to assert ports that
must not be called on a given path. If mocking more than 4–5 ports for a single use-case, treat
it as an SRP signal.

**Constructor injection in Tier 2 tests.** Construct the service explicitly in `@BeforeEach`
rather than using `@InjectMocks`. This produces a compile error when dependencies change,
makes test setup intent explicit, and aligns with constructor-injection architecture.

**Test data builders — Object Mother pattern.** Create a `<Domain>TestFixtures` class in
`test/<subdomain>/` with static factory methods for each canonical test state. This eliminates
construction duplication across test classes and keeps tests focused on the scenario being tested.

```java
// test/domain/session/SessionTestFixtures.java
public final class SessionTestFixtures {

    private SessionTestFixtures() {}

    public static Session pendingSession() {
        return Session.create(campaignId(), ownerId(), 1);
    }

    public static Session draftSession() {
        Session s = pendingSession();
        s.transitionTo(SessionStatus.PROCESSING);
        s.transitionTo(SessionStatus.DRAFT);
        return s;
    }

    public static Session committedSession() {
        Session s = draftSession();
        s.transitionTo(SessionStatus.COMMITTED);
        return s;
    }

    public static UUID campaignId() { return UUID.fromString("00000000-0000-0000-0000-000000000001"); }
    public static UUID ownerId()    { return UUID.fromString("00000000-0000-0000-0000-000000000002"); }
}
```

Use these across all test tiers. Domain tests, application tests, and persistence tests all use
the same fixture factories — no duplication.

## Common Pitfalls

- **Using `@SpringBootTest` for domain or application layer tests.** This spins up the full
  Spring context, loads all beans, and connects to the database. It is wrong for unit-level tests.
  Use `@ExtendWith(MockitoExtension.class)` for application tests.

- **Using `@InjectMocks` instead of explicit constructor injection.** `@InjectMocks` silently
  does nothing when Mockito cannot resolve injection. A new constructor parameter added to the
  service under test will not cause a compile error — it will cause a `NullPointerException` at
  runtime. Construct the service explicitly in `@BeforeEach`.

- **Not using the pgvector PostgreSQL image in Testcontainers.** Plain `postgres:16` does not
  include the pgvector extension. The `entity_embeddings` table migration will fail.

- **No test isolation between integration test methods.** Without `@Transactional` rollback or an
  explicit truncation script, persisted rows from one test will affect the next. Default strategy
  is `@Transactional` on the test class; use `@Sql` cleanup when commit semantics must be tested.

- **Testing the happy path only.** Domain invariants derive their value from the unhappy paths.
  Test every exception branch in aggregate methods.

- **Using JUnit native `assertEquals` instead of AssertJ.** Failure messages from
  `assertEquals(expected, actual)` show raw values with no context. AssertJ's
  `assertThat(actual).isEqualTo(expected)` includes the field name and surrounding context in the
  failure output. Use AssertJ for all value assertions.

- **Asserting query results synchronously after a commit.** Embedding generation is async
  (D-063). Entity versions without embeddings are excluded from Query Mode retrieval. Mock
  `EmbeddingPort` in tests that exercise query retrieval after a commit.

- **Modifying an ArchUnit rule to make it pass.** A failing ArchUnit test means production code
  violated the architecture. Fix the production code.

- **Running PITest as part of normal development loop.** PITest is slow. Run it deliberately
  when modifying domain logic, not on every save. CI runs it automatically.

- **Raising `mutationThreshold` to pass a build.** A threshold bump without a written
  justification in `DECISIONS.md` is a red flag. Diagnose surviving mutants first.

## References

- `apps/api/CLAUDE.md` §7 TEST-01 convention
- `DECISIONS.md` D-036 (PITest), D-037 (ArchUnit), D-056 (no E2E)
- `ARCHITECTURE.md` §3.2 (hexagonal architecture rules)
- `backend-domain-model` skill (domain entity test examples)
- `backend-endpoint` skill (application service test examples)
