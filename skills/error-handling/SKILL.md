---
name: error-handling
description: >
  Use this skill whenever you are implementing validation, error responses, or exception handling
  in `apps/api`. Triggers include: "GlobalExceptionHandler", "domain exception", "error response",
  "validation", "Bean Validation", "@NotNull", "@AssertTrue", "@ControllerAdvice", "422 error",
  "400 error", "error code", "three-tier validation", "VALID-01", or any task involving how
  errors are caught and returned from the backend. This skill covers the full three-tier validation
  model, the domain exception hierarchy, all project-specific error codes, and the GlobalExceptionHandler
  mapping pattern.
---

# Backend — Error Handling and Validation

Blue Steel uses a three-tier validation model (VALID-01) where each layer catches errors
appropriate to its knowledge. No single tier is the sole gatekeeper — all three must be present.

## The Three Tiers (VALID-01)

| Tier | Layer | What it catches | Mechanism | HTTP status |
|---|---|---|---|---|
| **Format** | REST controller | Null checks, size bounds, enum membership, cross-field constraints | Bean Validation (`@NotNull`, `@Size`, `@AssertTrue`, custom) | 400 |
| **Preconditions** | Application service | Cross-aggregate rules requiring DB reads | Unchecked domain exceptions caught by `GlobalExceptionHandler` | 409 / 422 |
| **Invariants** | Domain entity / aggregate | Single-object business rules from the object's own state | Unchecked domain exceptions thrown in constructors and methods | 422 (via handler) |

**Rules:**
- A rule that requires a DB read cannot live in the domain — it belongs in the application service.
- A rule derivable from the object's own state **must** live in the domain.
- The controller never contains business logic. Services never contain format validation.

---

## Error Response Envelope (ERR-01)

Every error response — regardless of tier — uses this envelope:

```json
{
  "errors": [
    {
      "code": "MACHINE_READABLE_CONSTANT",
      "message": "Human-readable description for display",
      "field": "fieldName or null"
    }
  ]
}
```

- `code` — machine-readable constant, ALL_CAPS_SNAKE_CASE, used by clients for programmatic handling
- `message` — human-readable string, safe to display to users
- `field` — populated for validation errors tied to a specific field; null otherwise
- Never leak stack traces, class names, or internal implementation details

---

## HTTP Status Map

| Status | When to use |
|---|---|
| 400 | Format validation failure (Bean Validation) |
| 401 | Unauthenticated (no valid JWT) |
| 403 | Authenticated but insufficient role for platform-level operation |
| 404 | Resource not found |
| 409 | State conflict (e.g. duplicate active draft per campaign, duplicate GM per campaign) |
| 422 | Business rule violation (well-formed request that violates a domain invariant or precondition) |
| 500 | Unhandled internal error |
| 504 | Query timeout (D-052) |

---

## Domain Exception Hierarchy

Domain and application layers use **unchecked exceptions only** (ERR-02). No `throws` declarations.

```java
// domain/shared/DomainException.java — base for all domain exceptions
public class DomainException extends RuntimeException {
    public DomainException(String message) { super(message); }
    public DomainException(String message, Throwable cause) { super(message, cause); }
}

// Examples of specific domain exceptions:
public class EntityNotFoundException extends DomainException { ... }     // → 404
public class UnauthorizedException extends DomainException { ... }       // → 403
public class ConflictException extends DomainException { ... }           // → 409
public class BusinessRuleViolationException extends DomainException {    // → 422
    private final ErrorCode errorCode;
    public BusinessRuleViolationException(ErrorCode code, String message) { ... }
}
public class InvalidSessionTransitionException extends DomainException { ... }  // → 422
public class QueryTimeoutException extends DomainException { ... }       // → 504
```

---

## GlobalExceptionHandler

All domain exceptions are mapped to HTTP responses in a single `@ControllerAdvice` class:

```java
// adapters/in/web/GlobalExceptionHandler.java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleNotFound(EntityNotFoundException ex) {
        return ApiErrorResponse.of(ErrorCode.ENTITY_NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ApiErrorResponse handleBusinessRule(BusinessRuleViolationException ex) {
        return ApiErrorResponse.of(ex.errorCode(), ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleConflict(ConflictException ex) {
        return ApiErrorResponse.of(ErrorCode.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(QueryTimeoutException.class)
    @ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
    public ApiErrorResponse handleQueryTimeout(QueryTimeoutException ex) {
        return ApiErrorResponse.of(ErrorCode.QUERY_TIMEOUT, "The query timed out. Try rephrasing.");
    }

    // Catch-all: never leak internals
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiErrorResponse handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ApiErrorResponse.of(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred.");
    }
}
```

```java
// adapters/in/web/ApiErrorResponse.java
public record ApiErrorResponse(List<ApiError> errors) {
    public static ApiErrorResponse of(ErrorCode code, String message) {
        return new ApiErrorResponse(List.of(new ApiError(code.name(), message, null)));
    }
    public static ApiErrorResponse ofField(ErrorCode code, String message, String field) {
        return new ApiErrorResponse(List.of(new ApiError(code.name(), message, field)));
    }
}

public record ApiError(String code, String message, String field) {}
```

---

## Bean Validation — Tier 1

Apply Bean Validation to request DTOs (Java Records) in `adapters/in/web/`:

```java
// Standard annotations
public record CommitSessionRequest(
    @NotNull List<@Valid CardDecisionRequest> cardDecisions,
    @NotNull List<@Valid UncertainResolutionRequest> uncertainResolutions,
    @NotNull List<@Valid ConflictAcknowledgmentRequest> acknowledgedConflicts
) {}

// Cross-field constraint — @AssertTrue for complex validation
public record UncertainResolutionRequest(
    @NotNull String cardId,
    @NotNull String resolution,
    String matchedEntityId
) {
    @AssertTrue(message = "matched_entity_id is required when resolution is MATCH")
    public boolean isMatchedEntityIdValidForResolution() {
        return !"MATCH".equals(resolution) || matchedEntityId != null;
    }
}
```

Spring MVC returns 400 with `MethodArgumentNotValidException` when Bean Validation fails.
Handle it in `GlobalExceptionHandler`:

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
@ResponseStatus(HttpStatus.BAD_REQUEST)
public ApiErrorResponse handleValidation(MethodArgumentNotValidException ex) {
    List<ApiError> errors = ex.getBindingResult().getFieldErrors().stream()
        .map(e -> new ApiError(ErrorCode.VALIDATION_ERROR.name(), e.getDefaultMessage(), e.getField()))
        .toList();
    return new ApiErrorResponse(errors);
}
```

---

## Project-Specific Error Codes

These error codes are referenced across skills and must be consistent:

| Code | HTTP | When |
|---|---|---|
| `UNKNOWN_CARD_ID` | 422 | `card_id` in commit payload not found in stored diff |
| `DUPLICATE_CARD_DECISION` | 422 | Duplicate `card_id` entries in `card_decisions` |
| `INCOMPLETE_CARD_DECISIONS` | 422 | Non-UNCERTAIN card in diff has no entry in `card_decisions` |
| `UNCERTAIN_ENTITIES_PRESENT` | 422 | UNCERTAIN cards not resolved in `uncertain_resolutions` |
| `CONFLICTS_NOT_ACKNOWLEDGED` | 422 | ConflictCards in diff not acknowledged |
| `INVALID_ENTITY_REFERENCE` | 422 | `matched_entity_id` references entity from different campaign |
| `UNSUPPORTED_ACTION` | 422 | `add` action in commit payload (v1 restriction, D-053) |
| `INVALID_SESSION_STATE` | 422 | Operation not valid for current session status |
| `SUMMARY_TOO_LARGE` | 400 | Submitted summary exceeds token budget |
| `ACTIVE_DRAFT_EXISTS` | 409 | New session submitted while one is `processing` or `draft` |
| `ENTITY_NOT_FOUND` | 404 | Entity not found or not in scope of requested campaign |
| `QUERY_TIMEOUT` | 504 | Query pipeline LLM call exceeded configured timeout |

Define these as an enum in `adapters/in/web/ErrorCode.java`.

---

## Common Mistakes to Avoid

- **Putting a business logic check only in the controller.** The controller strips it on the next
  refactor. Business rules belong in the application service or domain.

- **Leaking exception type names or stack traces in error messages.** These expose internal
  structure. Catch and translate at `GlobalExceptionHandler`.

- **Using `@ResponseStatus` on domain exception classes.** Domain exceptions must have zero Spring
  imports (ARCH-01). The HTTP mapping lives exclusively in `GlobalExceptionHandler`.

- **Missing a `field` value on validation errors.** Field-level validation errors without `field`
  populated make client-side form error mapping impossible. Always populate `field` from
  `FieldError.getField()`.

- **Returning 500 for business rule violations.** Business rule violations that the user can fix
  are 422, not 500. A `NullPointerException` inside a use case is a 500 (bug). An entity reference
  that doesn't belong to the campaign is a 422 (invalid input).

## References

- `apps/api/CLAUDE.md` §6 (VALID-01, ERR-01, ERR-02 conventions)
- `ARCHITECTURE.md` §7.4 (error model)
- `DECISIONS.md` D-078, D-079, D-080, D-081
- `backend-endpoint` skill (controller wiring + exception handler registration)
- `session-ingestion-pipeline` skill (CommitService 8 mandatory validation preconditions)
