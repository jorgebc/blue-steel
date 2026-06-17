package com.bluesteel.application.model.query;

import java.util.List;

/**
 * Paginated read model for a campaign's Q&amp;A history. {@code totalCount} is the total number of
 * log entries across all pages; {@code page}/{@code size} echo the applied offset-pagination window
 * (D-055). {@code entries} are newest first.
 */
public record QueryHistoryView(List<QueryLogEntry> entries, long totalCount, int page, int size) {}
