package com.bluesteel.application.model.query;

/**
 * Zero-based offset paging parameters for the Q&amp;A history listing (D-055). {@code page} is the
 * zero-based page index; {@code size} is the page length.
 */
public record QueryHistoryPage(int page, int size) {}
