package com.bluesteel.application.model.proposal;

/**
 * Zero-based offset paging parameters for proposal listings (D-055). {@code page} is the zero-based
 * page index; {@code size} is the page length.
 */
public record ProposalPage(int page, int size) {}
