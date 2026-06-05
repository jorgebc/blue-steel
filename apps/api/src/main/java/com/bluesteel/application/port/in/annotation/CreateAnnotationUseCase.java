package com.bluesteel.application.port.in.annotation;

import com.bluesteel.application.model.annotation.AnnotationView;
import com.bluesteel.application.model.annotation.CreateAnnotationCommand;

/** Creates a new annotation on a world-state entity. Any campaign member may post. */
public interface CreateAnnotationUseCase {

  AnnotationView create(CreateAnnotationCommand command);
}
