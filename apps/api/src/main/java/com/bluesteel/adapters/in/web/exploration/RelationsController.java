package com.bluesteel.adapters.in.web.exploration;

import com.bluesteel.adapters.in.web.ApiResponse;
import com.bluesteel.application.model.worldstate.RelationDetailView;
import com.bluesteel.application.port.in.worldstate.GetRelationDetailUseCase;
import com.bluesteel.application.port.in.worldstate.ListRelationsUseCase;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only Exploration Mode endpoints for the relations graph (D-010, D-030). The list optionally
 * filters to relations touching a given actor/space; each relation carries its structured graph
 * endpoints and the detail returns the full version history.
 */
@RestController
@RequestMapping("/api/v1/campaigns/{id}/relations")
public class RelationsController {

  private final ListRelationsUseCase listRelationsUseCase;
  private final GetRelationDetailUseCase getRelationDetailUseCase;

  public RelationsController(
      ListRelationsUseCase listRelationsUseCase,
      GetRelationDetailUseCase getRelationDetailUseCase) {
    this.listRelationsUseCase = listRelationsUseCase;
    this.getRelationDetailUseCase = getRelationDetailUseCase;
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<RelationResponse>>> listRelations(
      @PathVariable UUID id, @RequestParam(required = false) UUID actor) {
    UUID callerId = resolveCallerId();
    List<RelationResponse> relations =
        listRelationsUseCase.list(id, callerId, actor).stream()
            .map(RelationResponse::from)
            .toList();
    return ResponseEntity.ok(ApiResponse.success(relations));
  }

  @GetMapping("/{rid}")
  public ResponseEntity<ApiResponse<RelationDetailResponse>> getRelation(
      @PathVariable UUID id, @PathVariable UUID rid) {
    UUID callerId = resolveCallerId();
    RelationDetailView view = getRelationDetailUseCase.getDetail(id, rid, callerId);
    return ResponseEntity.ok(ApiResponse.success(RelationDetailResponse.from(view)));
  }

  private UUID resolveCallerId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return UUID.fromString(auth.getName());
  }
}
