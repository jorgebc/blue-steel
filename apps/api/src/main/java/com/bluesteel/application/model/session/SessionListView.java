package com.bluesteel.application.model.session;

import java.util.List;

/**
 * Paginated read model for the campaign session list. {@code totalCount} is the total number of
 * sessions in the campaign across all pages; {@code page}/{@code size} echo the applied
 * offset-pagination window (D-055).
 */
public record SessionListView(
    List<SessionSummaryView> sessions, long totalCount, int page, int size) {}
