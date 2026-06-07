package com.bluesteel.adapters.in.web.session;

import com.bluesteel.adapters.in.web.ApiResponse;
import com.bluesteel.application.model.commit.AcknowledgedConflict;
import com.bluesteel.application.model.commit.CardAction;
import com.bluesteel.application.model.commit.CardDecision;
import com.bluesteel.application.model.commit.CommitPayload;
import com.bluesteel.application.model.commit.ResolutionType;
import com.bluesteel.application.model.commit.UncertainResolution;
import com.bluesteel.application.model.session.CommitSessionCommand;
import com.bluesteel.application.model.session.DiffPayload;
import com.bluesteel.application.model.session.SessionDetailView;
import com.bluesteel.application.model.session.SessionListView;
import com.bluesteel.application.model.session.SessionStatusView;
import com.bluesteel.application.model.session.SubmitSessionCommand;
import com.bluesteel.application.model.session.SubmitSessionResult;
import com.bluesteel.application.port.in.session.CommitSessionUseCase;
import com.bluesteel.application.port.in.session.DiscardSessionUseCase;
import com.bluesteel.application.port.in.session.GetSessionDetailUseCase;
import com.bluesteel.application.port.in.session.GetSessionDiffUseCase;
import com.bluesteel.application.port.in.session.GetSessionStatusUseCase;
import com.bluesteel.application.port.in.session.ListSessionsUseCase;
import com.bluesteel.application.port.in.session.SubmitSessionUseCase;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
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
 * Handles session intake, status polling, draft discard, diff retrieval, and commit for a campaign.
 */
@RestController
@RequestMapping("/api/v1/campaigns/{id}/sessions")
public class SessionController {

  private final SubmitSessionUseCase submitSessionUseCase;
  private final ListSessionsUseCase listSessionsUseCase;
  private final GetSessionDetailUseCase getSessionDetailUseCase;
  private final GetSessionStatusUseCase getSessionStatusUseCase;
  private final DiscardSessionUseCase discardSessionUseCase;
  private final GetSessionDiffUseCase getSessionDiffUseCase;
  private final CommitSessionUseCase commitSessionUseCase;

  public SessionController(
      SubmitSessionUseCase submitSessionUseCase,
      ListSessionsUseCase listSessionsUseCase,
      GetSessionDetailUseCase getSessionDetailUseCase,
      GetSessionStatusUseCase getSessionStatusUseCase,
      DiscardSessionUseCase discardSessionUseCase,
      GetSessionDiffUseCase getSessionDiffUseCase,
      CommitSessionUseCase commitSessionUseCase) {
    this.submitSessionUseCase = submitSessionUseCase;
    this.listSessionsUseCase = listSessionsUseCase;
    this.getSessionDetailUseCase = getSessionDetailUseCase;
    this.getSessionStatusUseCase = getSessionStatusUseCase;
    this.discardSessionUseCase = discardSessionUseCase;
    this.getSessionDiffUseCase = getSessionDiffUseCase;
    this.commitSessionUseCase = commitSessionUseCase;
  }

  /** Submits a new session narrative; returns 202 Accepted with sessionId and initial status. */
  @PostMapping
  public ResponseEntity<ApiResponse<SessionAcceptedResponse>> submit(
      @PathVariable UUID id, @Valid @RequestBody SubmitSessionRequest request) {
    UUID callerId = resolveCallerId();
    SubmitSessionResult result =
        submitSessionUseCase.submit(new SubmitSessionCommand(callerId, id, request.summaryText()));
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(
            ApiResponse.success(new SessionAcceptedResponse(result.sessionId(), result.status())));
  }

  /** Lists the campaign's sessions (offset-paginated), accessible to any campaign member. */
  @GetMapping
  public ResponseEntity<ApiResponse<List<SessionSummaryResponse>>> list(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    UUID callerId = resolveCallerId();
    SessionListView view = listSessionsUseCase.list(id, callerId, page, size);
    List<SessionSummaryResponse> sessions =
        view.sessions().stream()
            .map(
                s ->
                    new SessionSummaryResponse(
                        s.sessionId(),
                        s.status(),
                        s.sequenceNumber(),
                        s.committedAt(),
                        s.createdAt()))
            .toList();
    Map<String, Object> meta =
        Map.of("page", view.page(), "size", view.size(), "totalCount", view.totalCount());
    return ResponseEntity.ok(ApiResponse.success(sessions, meta));
  }

  /** Returns a single session's detail, accessible to any campaign member. */
  @GetMapping("/{sid}")
  public ResponseEntity<ApiResponse<SessionDetailResponse>> getDetail(
      @PathVariable UUID id, @PathVariable UUID sid) {
    UUID callerId = resolveCallerId();
    SessionDetailView view = getSessionDetailUseCase.getDetail(sid, callerId, id);
    return ResponseEntity.ok(
        ApiResponse.success(
            new SessionDetailResponse(
                view.sessionId(),
                view.campaignId(),
                view.status(),
                view.sequenceNumber(),
                view.failureReason(),
                view.committedAt(),
                view.createdAt(),
                view.updatedAt(),
                view.narrativeBlockId(),
                view.narrativeSummary())));
  }

  /** Returns the current status of a session, accessible to any campaign member. */
  @GetMapping("/{sid}/status")
  public ResponseEntity<ApiResponse<SessionStatusResponse>> getStatus(
      @PathVariable UUID id, @PathVariable UUID sid) {
    UUID callerId = resolveCallerId();
    SessionStatusView view = getSessionStatusUseCase.getStatus(sid, callerId, id);
    return ResponseEntity.ok(
        ApiResponse.success(
            new SessionStatusResponse(
                view.sessionId(), view.status(), view.failureReason(), view.message())));
  }

  /** Discards a draft session (GM only); returns 200 on success. */
  @DeleteMapping("/{sid}")
  public ResponseEntity<ApiResponse<Void>> discard(@PathVariable UUID id, @PathVariable UUID sid) {
    UUID callerId = resolveCallerId();
    discardSessionUseCase.discard(sid, callerId, id);
    return ResponseEntity.ok(ApiResponse.success(null));
  }

  /** Returns the structured diff for a session in DRAFT status (GM or Editor only). */
  @GetMapping("/{sid}/diff")
  public ResponseEntity<ApiResponse<DiffPayload>> getDiff(
      @PathVariable UUID id, @PathVariable UUID sid) {
    UUID callerId = resolveCallerId();
    DiffPayload diff = getSessionDiffUseCase.getDiff(callerId, id, sid);
    return ResponseEntity.ok(ApiResponse.success(diff));
  }

  /** Commits a reviewed draft session to world state; returns 200 on success. */
  @PostMapping("/{sid}/commit")
  public ResponseEntity<ApiResponse<Void>> commit(
      @PathVariable UUID id,
      @PathVariable UUID sid,
      @Valid @RequestBody CommitSessionRequest request) {
    UUID callerId = resolveCallerId();
    commitSessionUseCase.commit(new CommitSessionCommand(callerId, id, sid, mapToPayload(request)));
    return ResponseEntity.ok(ApiResponse.success(null));
  }

  private static CommitPayload mapToPayload(CommitSessionRequest request) {
    List<CardDecision> decisions =
        request.cardDecisions() == null
            ? List.of()
            : request.cardDecisions().stream()
                .map(
                    d ->
                        new CardDecision(
                            d.cardId(), CardAction.fromJson(d.action()), d.editedFields()))
                .toList();

    List<UncertainResolution> resolutions =
        request.uncertainResolutions() == null
            ? List.of()
            : request.uncertainResolutions().stream()
                .map(
                    r ->
                        new UncertainResolution(
                            r.cardId(),
                            ResolutionType.valueOf(r.resolution().toUpperCase()),
                            r.matchedEntityId()))
                .toList();

    List<AcknowledgedConflict> acknowledged =
        request.acknowledgedConflicts() == null
            ? List.of()
            : request.acknowledgedConflicts().stream()
                .map(a -> new AcknowledgedConflict(a.conflictId()))
                .toList();

    return new CommitPayload(decisions, resolutions, acknowledged);
  }

  private UUID resolveCallerId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return UUID.fromString(auth.getName());
  }
}
