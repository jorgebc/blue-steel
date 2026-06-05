package com.bluesteel.adapters.in.web.annotation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Request body for {@code POST /api/v1/campaigns/{id}/annotations}. */
public record CreateAnnotationRequest(
    @NotNull(message = "entityType is required")
        @Pattern(
            regexp = "actor|space|event|relation",
            message = "entityType must be one of: actor, space, event, relation")
        String entityType,
    @NotNull(message = "entityId is required") UUID entityId,
    @NotBlank(message = "content must not be blank")
        @Size(max = 5000, message = "content must not exceed 5000 characters")
        String content) {}
