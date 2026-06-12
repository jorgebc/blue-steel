package com.bluesteel.adapters.in.web.query;

/**
 * Response body for {@code GET /api/v1/campaigns/{id}/queries/usage}. {@code consumedUsd}/{@code
 * capUsd} are the instance-wide shared daily LLM budget (D-096); {@code requestsRemaining} is how
 * many more questions the caller may ask in the current {@code windowSeconds} window before hitting
 * the per-(user,campaign) rate limit of {@code maxRequests}.
 */
public record QueryUsageResponse(
    double consumedUsd,
    double capUsd,
    int requestsRemaining,
    int maxRequests,
    long windowSeconds) {}
