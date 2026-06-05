package com.bluesteel.application.port.in.annotation;

import com.bluesteel.application.model.annotation.AnnotationView;
import java.util.List;
import java.util.UUID;

/** Lists all annotations on a given world-state entity within a campaign. */
public interface ListAnnotationsUseCase {

  List<AnnotationView> list(UUID campaignId, String entityType, UUID entityId, UUID callerId);
}
