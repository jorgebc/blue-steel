package com.bluesteel.application.model.query;

/**
 * Current shared LLM spend for Query Mode against the daily cost cap (D-096): {@code consumedUsd}
 * is the running total recorded for the current UTC day, {@code capUsd} the configured daily
 * ceiling. Instance-wide, not per campaign — surfaced so users can self-moderate against the shared
 * free tier.
 */
public record QueryUsage(double consumedUsd, double capUsd) {}
