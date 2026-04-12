---
name: backend-endpoint
description: >
  Use this skill whenever you are adding, modifying, or debugging a REST endpoint in `apps/api`.
  Triggers include: "add an endpoint", "implement the [X] API", "wire [use-case] to HTTP",
  "add a controller method", "create a REST resource", or any task that touches a Spring
  controller, use-case service, or driven port in the backend. This skill enforces the
  full Ports & Adapters layering: DTO → controller → driving port → use-case service →
  driven port → adapter. It is the primary reference for all backend feature work.
---

# Backend — Adding an API Endpoint End-to-End

Blue Steel's backend is a strict Hexagonal (Ports & Adapters) architecture. Every new endpoint
must flow through all five layers — driven by ArchUnit tests that enforce the boundaries on every
build (D-037). Shortcuts that violate the layer hierarchy will be caught at CI time.

## Context

**Architecture rules (never bypass):**

- `com.bluesteel.domain` has zero imports from `org.springframework.*` or `jakarta.persistence.*`
  (ARCH-01). ArchUnit enforces this.
- Ports are Java interfaces in `application.port.in` (driving) and `application.port.out` (driven).
  Application services implement driving ports. Adapters implement driven ports. No direct class
  coupling across layers (ARCH-02).
- Controllers call driving port interfaces — never service implementation classes directly.
- Authorization is resolved at the use-case boundary, not in the controller (AUTH-01). The JWT
  carries only `user_id` and `is_admin`; campaign role is fetched from `campaign_members` via DB
  on every campaign-scoped call (D-043).
- All error responses follow the standard envelope with machine-readable `code`, human-readable
  `message`, and nullable `field` (ERR-01). HTTP 422 is for business rule violations; 400 is for
  validation errors.
- All success responses use the envelope `{ "data": {}, "meta": {}, "errors": [] }` (API-01).

**Relevant decisions:** D-038 (package structure), D-039 (config co-location), D-043 (auth model),
AUTH-01, ARCH-01 through ARCH-04.

## Workflow

Follow these steps in order. Do not skip steps, especially the failing test step — TDD is not
optional (Kent Beck).

### 1. Define request and response DTOs

Create Java Records for the request and response shapes in
`adapters/in/web/<domain>/` (e.g., `CreateActorRequest.java`, `ActorResponse.java`).

- Records are preferred for all DTOs — they are immutable by construction (ARCHITECTURE.md §3.5).
- Annotate with Bean Validation (`@NotNull`, `@Size`, etc.) for input validation.
- Do not use domain objects as DTOs — they must never cross the adapter boundary.
- Use `UUID` for all ID fields, `Instant` for timestamps (serialised as ISO 8601 UTC).

### 2. Define the driving port (use-case interface)

Create a Java interface in `application/port/in/` for the new use case
(e.g., `CreateActorUseCase`). The interface takes a command or query object (a Record) as input
and returns a domain result or void.

- Name the interface after the use case, not the implementation: `CreateActorUseCase`, not
  `ActorService`.
- The command/query Record lives alongside the port interface in `application/port/in/`.
- The interface signature uses only domain types — no HTTP, no JPA, no Spring types.

### 3. Write a failing use-case unit test (TDD)

Before writing the service, write a unit test in `test/application/<domain>/` that:

- Instantiates the service under test.
- Mocks all driven ports using Mockito.
- Asserts the expected output or side effects.
- Does **not** start a Spring context (`@SpringBootTest` is forbidden here — TEST-01).

The test must fail at this point. That failure is the contract you are about to fulfil.

### 4. Implement the use-case service

Create the service class in `application/service/` implementing the driving port interface.

- The service orchestrates; it does not contain domain logic. Domain logic lives in domain entities.
- Inject driven ports via constructor injection — no field injection.
- Resolve campaign-level role via `CampaignMembershipPort` (a driven port) for any campaign-scoped
  operation. Do not trust JWT claims for authorization (AUTH-01).
- Use unchecked domain exceptions; no `throws` declarations (ERR-02).
- The service should now make the failing test pass.

### 5. Define driven port(s) if new persistence is needed

If the use case requires a new database query or a new table:

1. Define the driven port interface in `application/port/out/` (e.g., `ActorRepository`). The
   interface uses domain types — no JPA entities.
2. Add a persistence integration test in `test/adapters/persistence/` using Testcontainers
   **before** writing the adapter implementation (TEST-01 still applies here).
3. Implement the JPA adapter in `adapters/out/persistence/<domain>/`. Create the JPA entity
   (annotated with `@Entity` etc. — JPA annotations live only here), the Spring Data repository
   interface, and the mapper (domain object ↔ JPA entity).

See `database-migration` skill for adding the Liquibase changeset.

### 6. Add the REST controller method

Add or extend the controller in `adapters/in/web/<domain>/<Domain>Controller.java`:

- Annotate with `@RestController` and `@RequestMapping("/api/v1/campaigns/{id}/...")`.
- Inject the **driving port interface** — not the service class.
- Extract `user_id` and `is_admin` from the `SecurityContext` (set by the JWT filter).
- Map the HTTP request to the command Record. Map the domain result to the response DTO.
- Wrap the response in the standard envelope before returning.
- Do not contain any business logic in the controller.

### 7. Register error codes and exception mappings

In `adapters/in/web/GlobalExceptionHandler.java`:

- Add a new `@ExceptionHandler` method for any new domain exceptions introduced.
- Map to the correct HTTP status and error code constant.
- Never leak stack traces or internal class names in error messages.

### 8. Verify ArchUnit passes

Run the ArchUnit test suite (fast, no Testcontainers):

```bash
# [Command TBD before Phase 1 — see apps/api/CLAUDE.md §4]
mvn test -Dtest=ArchitectureTest
```

A failing ArchUnit test means a layer boundary was violated. Fix the violation; do not adjust
the ArchUnit rule to accommodate the violation.

### 9. Run the full test suite

```bash
# Unit + ArchUnit
mvn test

# Integration tests (Testcontainers — needs Docker)
mvn verify
```

All tests must be green before the endpoint is considered complete.

## Patterns & Conventions

**Naming (NAMING-01):**

| Artefact | Convention | Example |
|---|---|---|
| Driving port | Interface, verb-noun, suffixed `UseCase` | `CommitSessionUseCase` |
| Service | Implements driving port, suffixed `Service` | `CommitSessionService` |
| Controller | Suffixed `Controller` | `SessionController` |
| Request DTO | Suffixed `Request` | `CommitSessionRequest` |
| Response DTO | Suffixed `Response` | `SessionStatusResponse` |
| Driven port | Interface, noun or noun-verb, suffixed `Port` or `Repository` | `NarrativeExtractionPort`, `ActorRepository` |
| JPA entity | Suffixed `Entity` or `Jpa` | `SessionEntity` / `SessionJpa` |
| Mapper | Suffixed `Mapper` | `SessionMapper` |

**REST URL conventions:**
- Base: `/api/v1/`
- Plural nouns: `/campaigns`, `/sessions`, `/actors`
- Nested ownership: `/campaigns/{id}/sessions/{sid}/diff`
- Kebab-case segments, no trailing slash

**HTTP status conventions (ERR-01):**
- 200 success, 201 created, 400 validation, 401 unauth, 403 unauthorized, 404 not found,
  409 conflict, 422 business rule violation, 500 internal

**Authorization pattern (AUTH-01):**

```java
// Controller extracts identity from SecurityContext
UUID userId = SecurityContextHolder.getContext().getAuthentication()...;

// Use-case service resolves campaign role via driven port
CampaignRole role = campaignMembershipPort.resolveRole(campaignId, userId);
if (role != CampaignRole.GM && role != CampaignRole.EDITOR) {
    throw new UnauthorizedException("...");
}
```

## Examples

**Adding `DELETE /api/v1/campaigns/{id}/sessions/{sid}` (discard draft):**

1. DTO: No request body. Response: 200 with minimal session DTO or 204.
2. Driving port: `DiscardDraftSessionUseCase.discard(UUID campaignId, UUID sessionId, UUID callerId)`.
3. Test: mock `SessionRepository`, assert `SessionRepository.save()` is called with status
   `DISCARDED`, assert throws `UnauthorizedException` when caller is not GM.
4. Service: fetch session, verify status is `DRAFT`, verify caller is `GM` in campaign, transition
   status to `DISCARDED`, clear `diffPayload`, save.
5. No new driven ports needed — `SessionRepository` already exists.
6. Controller: `@DeleteMapping("/{sid}")` → extract userId from context → call use case.
7. Exception handler: map `SessionNotInDraftException` to `422 INVALID_SESSION_STATE`.

## Common Pitfalls

- **Calling the service class directly in the controller.** The controller must inject the driving
  port *interface*, not `CommitSessionService`. If you see `@Autowired CommitSessionService service`
  in a controller, that is a violation.

- **Putting business logic in the controller.** Role checks, state transitions, and invariant
  enforcement belong in the use-case service or domain entities. The controller should be thin.

- **Using JPA entities in the application layer.** The application layer must never import
  `com.bluesteel.adapters.*`. Mappers translate at the adapter boundary.

- **Using `@SpringBootTest` for unit tests.** Domain and application layer tests are pure JUnit +
  Mockito. A full Spring context is heavyweight and masks real test isolation failures.

- **Skipping the ArchUnit check.** After adding a new class, always run ArchUnit. Import mistakes
  (e.g., accidentally importing a Spring type in domain code) only appear there.

- **Forgetting `acknowledged_conflicts` in commit payload.** See `session-ingestion-pipeline`
  skill for the full commit validation rules (D-033, D-042).

## References

- `apps/api/CLAUDE.md` §7 (all ARCH-*, NAMING-*, ERR-*, AUTH-* conventions)
- `apps/api/CLAUDE.md` §9 "Adding a new API endpoint end-to-end"
- `ARCHITECTURE.md` §3 (hexagonal architecture), §7 (API conventions and endpoint catalogue)
- `DECISIONS.md` D-038, D-039, D-043, D-059
