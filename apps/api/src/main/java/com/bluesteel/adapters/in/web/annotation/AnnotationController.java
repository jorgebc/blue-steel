package com.bluesteel.adapters.in.web.annotation;

import com.bluesteel.adapters.in.web.ApiResponse;
import com.bluesteel.application.model.annotation.AnnotationView;
import com.bluesteel.application.model.annotation.CreateAnnotationCommand;
import com.bluesteel.application.port.in.annotation.CreateAnnotationUseCase;
import com.bluesteel.application.port.in.annotation.DeleteAnnotationUseCase;
import com.bluesteel.application.port.in.annotation.ListAnnotationsUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for campaign annotations (D-011):
 *
 * <ul>
 *   <li>{@code POST /api/v1/campaigns/{id}/annotations} — create
 *   <li>{@code GET /api/v1/campaigns/{id}/annotations?entityType=&entityId=} — list by entity
 *   <li>{@code DELETE /api/v1/campaigns/{id}/annotations/{aid}} — delete
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/campaigns/{id}/annotations")
public class AnnotationController {

  private final CreateAnnotationUseCase createAnnotationUseCase;
  private final ListAnnotationsUseCase listAnnotationsUseCase;
  private final DeleteAnnotationUseCase deleteAnnotationUseCase;

  public AnnotationController(
      CreateAnnotationUseCase createAnnotationUseCase,
      ListAnnotationsUseCase listAnnotationsUseCase,
      DeleteAnnotationUseCase deleteAnnotationUseCase) {
    this.createAnnotationUseCase = createAnnotationUseCase;
    this.listAnnotationsUseCase = listAnnotationsUseCase;
    this.deleteAnnotationUseCase = deleteAnnotationUseCase;
  }

  @PostMapping
  public ResponseEntity<ApiResponse<AnnotationResponse>> create(
      @PathVariable UUID id, @RequestBody @Valid CreateAnnotationRequest request) {
    UUID callerId = resolveCallerId();
    CreateAnnotationCommand command =
        new CreateAnnotationCommand(
            id, request.entityId(), request.entityType(), request.content(), callerId);
    AnnotationView view = createAnnotationUseCase.create(command);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(AnnotationResponse.from(view)));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<AnnotationResponse>>> list(
      @PathVariable UUID id,
      @RequestParam
          @NotNull(message = "entityType is required")
          @Pattern(
              regexp = "actor|space|event|relation",
              message = "entityType must be one of: actor, space, event, relation")
          String entityType,
      @RequestParam @NotNull(message = "entityId is required") UUID entityId) {
    UUID callerId = resolveCallerId();
    List<AnnotationResponse> responses =
        listAnnotationsUseCase.list(id, entityType, entityId, callerId).stream()
            .map(AnnotationResponse::from)
            .toList();
    return ResponseEntity.ok(ApiResponse.success(responses));
  }

  @DeleteMapping("/{aid}")
  public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id, @PathVariable UUID aid) {
    UUID callerId = resolveCallerId();
    deleteAnnotationUseCase.delete(id, aid, callerId);
    return ResponseEntity.ok(ApiResponse.success(null));
  }

  private UUID resolveCallerId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return UUID.fromString(auth.getName());
  }
}
