package com.bluesteel.application.port.in.annotation;

import java.util.UUID;

/** Deletes an annotation. The caller must be the annotation's author or a campaign GM. */
public interface DeleteAnnotationUseCase {

  void delete(UUID campaignId, UUID annotationId, UUID callerId);
}
