package com.bluesteel.adapters.in.web.query;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for {@code POST /api/v1/campaigns/{id}/queries}. */
public record QueryRequest(@NotBlank @Size(max = 2000) String question) {}
