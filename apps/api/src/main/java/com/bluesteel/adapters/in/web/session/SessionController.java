package com.bluesteel.adapters.in.web.session;

import com.bluesteel.adapters.in.web.ApiResponse;
import com.bluesteel.application.model.session.DiffPayload;
import com.bluesteel.application.model.session.SessionStatusView;
import com.bluesteel.application.model.session.SubmitSessionCommand;
import com.bluesteel.application.model.session.SubmitSessionResult;
import com.bluesteel.application.port.in.session.DiscardSessionUseCase;
import com.bluesteel.application.port.in.session.GetSessionDiffUseCase;
import com.bluesteel.application.port.in.session.GetSessionStatusUseCase;
import com.bluesteel.application.port.in.session.SubmitSessionUseCase;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RestController;

/** Handles session intake, status polling, draft discard, and diff retrieval for a campaign. */
@RestController
@RequestMapping("/api/v1/campaigns/{id}/sessions")
public class SessionController {

  private final SubmitSessionUseCase submitSessionUseCase;
  private final GetSessionStatusUseCase getSessionStatusUseCase;
  private final DiscardSessionUseCase discardSessionUseCase;
  private final GetSessionDiffUseCase getSessionDiffUseCase;

  public SessionController(
      SubmitSessionUseCase submitSessionUseCase,
      GetSessionStatusUseCase getSessionStatusUseCase,
      DiscardSessionUseCase discardSessionUseCase,
      GetSessionDiffUseCase getSessionDiffUseCase) {
    this.submitSessionUseCase = submitSessionUseCase;
    this.getSessionStatusUseCase = getSessionStatusUseCase;
    this.discardSessionUseCase = discardSessionUseCase;
    this.getSessionDiffUseCase = getSessionDiffUseCase;
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

  private UUID resolveCallerId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return UUID.fromString(auth.getName());
  }
}
