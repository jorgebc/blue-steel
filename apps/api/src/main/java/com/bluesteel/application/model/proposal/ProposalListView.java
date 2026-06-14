package com.bluesteel.application.model.proposal;

import java.util.List;

/**
 * Paginated read model for the campaign proposal list. {@code totalCount} is the total number of
 * matching proposals across all pages; {@code page}/{@code size} echo the applied offset-pagination
 * window (D-055).
 */
public record ProposalListView(List<ProposalView> proposals, long totalCount, int page, int size) {}
