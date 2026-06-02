package com.bluesteel.adapters.in.web.exploration;

import com.bluesteel.adapters.in.web.ApiResponse;
import com.bluesteel.application.model.worldstate.EntityDetailView;
import com.bluesteel.application.model.worldstate.EntityListPage;
import com.bluesteel.application.port.in.worldstate.GetEntityDetailUseCase;
import com.bluesteel.application.port.in.worldstate.ListEntitiesUseCase;
import java.util.List;
import java.util.Map;
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
 * Read-only Exploration Mode endpoints for the core world-state entity types (D-010). Each list is
 * offset-paginated (D-055) and carries {@code meta {page, size, totalCount}}; each detail returns
 * the entity's full version history. Events expose list+detail for click-through from the Timeline
 * but have no standalone list view.
 */
@RestController
@RequestMapping("/api/v1/campaigns/{id}")
public class WorldStateExplorationController {

  private final ListEntitiesUseCase listEntitiesUseCase;
  private final GetEntityDetailUseCase getEntityDetailUseCase;

  public WorldStateExplorationController(
      ListEntitiesUseCase listEntitiesUseCase, GetEntityDetailUseCase getEntityDetailUseCase) {
    this.listEntitiesUseCase = listEntitiesUseCase;
    this.getEntityDetailUseCase = getEntityDetailUseCase;
  }

  @GetMapping("/actors")
  public ResponseEntity<ApiResponse<List<EntitySummaryResponse>>> listActors(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return listResponse("actor", id, page, size);
  }

  @GetMapping("/actors/{aid}")
  public ResponseEntity<ApiResponse<EntityDetailResponse>> getActor(
      @PathVariable UUID id, @PathVariable UUID aid) {
    return detailResponse("actor", id, aid);
  }

  @GetMapping("/spaces")
  public ResponseEntity<ApiResponse<List<EntitySummaryResponse>>> listSpaces(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return listResponse("space", id, page, size);
  }

  @GetMapping("/spaces/{sid}")
  public ResponseEntity<ApiResponse<EntityDetailResponse>> getSpace(
      @PathVariable UUID id, @PathVariable UUID sid) {
    return detailResponse("space", id, sid);
  }

  @GetMapping("/events")
  public ResponseEntity<ApiResponse<List<EntitySummaryResponse>>> listEvents(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return listResponse("event", id, page, size);
  }

  @GetMapping("/events/{eid}")
  public ResponseEntity<ApiResponse<EntityDetailResponse>> getEvent(
      @PathVariable UUID id, @PathVariable UUID eid) {
    return detailResponse("event", id, eid);
  }

  private ResponseEntity<ApiResponse<List<EntitySummaryResponse>>> listResponse(
      String entityType, UUID campaignId, int page, int size) {
    UUID callerId = resolveCallerId();
    EntityListPage view = listEntitiesUseCase.list(entityType, campaignId, callerId, page, size);
    List<EntitySummaryResponse> items =
        view.items().stream().map(EntitySummaryResponse::from).toList();
    Map<String, Object> meta =
        Map.of("page", view.page(), "size", view.size(), "totalCount", view.totalCount());
    return ResponseEntity.ok(ApiResponse.success(items, meta));
  }

  private ResponseEntity<ApiResponse<EntityDetailResponse>> detailResponse(
      String entityType, UUID campaignId, UUID entityId) {
    UUID callerId = resolveCallerId();
    EntityDetailView view =
        getEntityDetailUseCase.getDetail(entityType, campaignId, entityId, callerId);
    return ResponseEntity.ok(ApiResponse.success(EntityDetailResponse.from(view)));
  }

  private UUID resolveCallerId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return UUID.fromString(auth.getName());
  }
}
