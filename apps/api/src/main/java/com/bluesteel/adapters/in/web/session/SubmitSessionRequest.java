package com.bluesteel.adapters.in.web.session;

import jakarta.validation.constraints.NotBlank;

/** Request body for submitting a new session narrative. */
public record SubmitSessionRequest(@NotBlank String summaryText) {}
