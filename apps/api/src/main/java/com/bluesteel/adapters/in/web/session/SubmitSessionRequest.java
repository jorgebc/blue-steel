package com.bluesteel.adapters.in.web.session;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for submitting a new session narrative. */
public record SubmitSessionRequest(
    // Generous bound ahead of the domain token-count check (SummaryTooLargeException); rejects
    // grossly oversized bodies at the controller before any processing.
    @NotBlank @Size(max = 50000) String summaryText) {}
