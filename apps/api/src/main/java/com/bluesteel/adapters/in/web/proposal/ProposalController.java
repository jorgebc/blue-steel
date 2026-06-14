package com.bluesteel.adapters.in.web.proposal;

import com.bluesteel.adapters.in.web.ApiResponse;
import com.bluesteel.application.model.proposal.CoSignProposalCommand;
import com.bluesteel.application.model.proposal.CreateProposalCommand;
import com.bluesteel.application.model.proposal.ProposalListView;
import com.bluesteel.application.model.proposal.ProposalView;
import com.bluesteel.application.port.in.proposal.CoSignProposalUseCase;
import com.bluesteel.application.port.in.proposal.CreateProposalUseCase;
import com.bluesteel.application.port.in.proposal.ListProposalsUseCase;
import com.bluesteel.domain.proposal.ProposalStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Handles proposal submission, listing, and co-signing for a campaign. */
@RestController
@RequestMapping("/api/v1/campaigns/{id}/proposals")
public class ProposalController {

  private final CreateProposalUseCase createProposalUseCase;
  private final ListProposalsUseCase listProposalsUseCase;
  private final CoSignProposalUseCase coSignProposalUseCase;
  private final ObjectMapper objectMapper;

  public ProposalController(
      CreateProposalUseCase createProposalUseCase,
      ListProposalsUseCase listProposalsUseCase,
      CoSignProposalUseCase coSignProposalUseCase,
      ObjectMapper objectMapper) {
    this.createProposalUseCase = createProposalUseCase;
    this.listProposalsUseCase = listProposalsUseCase;
    this.coSignProposalUseCase = coSignProposalUseCase;
    this.objectMapper = objectMapper;
  }

  /** Submits a new proposal; returns 201 Created with the persisted proposal. */
  @PostMapping
  public ResponseEntity<ApiResponse<ProposalResponse>> create(
      @PathVariable UUID id, @Valid @RequestBody CreateProposalRequest request) {
    UUID callerId = resolveCallerId();
    ProposalView view =
        createProposalUseCase.create(
            new CreateProposalCommand(
                callerId,
                id,
                request.targetType(),
                request.targetId(),
                request.sessionId(),
                request.proposedDelta()));
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(ProposalResponse.from(view, objectMapper)));
  }

  /** Lists the campaign's proposals (offset-paginated), accessible to any campaign member. */
  @GetMapping
  public ResponseEntity<ApiResponse<List<ProposalResponse>>> list(
      @PathVariable UUID id,
      @RequestParam(required = false) ProposalStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    UUID callerId = resolveCallerId();
    ProposalListView view = listProposalsUseCase.list(id, callerId, status, page, size);
    List<ProposalResponse> proposals =
        view.proposals().stream().map(p -> ProposalResponse.from(p, objectMapper)).toList();
    Map<String, Object> meta =
        Map.of("page", view.page(), "size", view.size(), "totalCount", view.totalCount());
    return ResponseEntity.ok(ApiResponse.success(proposals, meta));
  }

  /** Casts a co-sign vote; returns 200 with the updated proposal (status now cosigned). */
  @PostMapping("/{pid}/votes")
  public ResponseEntity<ApiResponse<ProposalResponse>> coSign(
      @PathVariable UUID id, @PathVariable UUID pid) {
    UUID callerId = resolveCallerId();
    ProposalView view = coSignProposalUseCase.coSign(new CoSignProposalCommand(callerId, id, pid));
    return ResponseEntity.ok(ApiResponse.success(ProposalResponse.from(view, objectMapper)));
  }

  private UUID resolveCallerId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return UUID.fromString(auth.getName());
  }
}
