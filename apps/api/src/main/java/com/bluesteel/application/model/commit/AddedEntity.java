package com.bluesteel.application.model.commit;

import java.util.Map;

/**
 * An entity the reviewer adds during diff review that the extraction missed (D-053). Carried on the
 * commit payload's {@code addedEntities} list and written as a brand-new entity + first version at
 * commit. Mirrors the camelCase JSON shape exactly — no {@code @JsonProperty} annotations.
 */
public record AddedEntity(String entityType, String name, Map<String, Object> fields) {}
