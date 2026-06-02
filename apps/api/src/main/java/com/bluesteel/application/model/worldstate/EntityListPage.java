package com.bluesteel.application.model.worldstate;

import java.util.List;

/** One offset-paginated page of entity summaries plus the total count across all pages (D-055). */
public record EntityListPage(List<EntitySummaryView> items, int page, int size, long totalCount) {}
