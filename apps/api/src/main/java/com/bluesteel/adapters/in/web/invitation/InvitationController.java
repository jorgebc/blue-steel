package com.bluesteel.adapters.in.web.invitation;

import com.bluesteel.adapters.in.web.ApiResponse;
import com.bluesteel.application.model.user.InvitationResult;
import com.bluesteel.application.model.user.InvitePlatformUserCommand;
import com.bluesteel.application.port.in.user.InvitePlatformUserUseCase;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Handles platform-level user invitation (admin only). */
@RestController
@RequestMapping("/api/v1/invitations")
public class InvitationController {

  private final InvitePlatformUserUseCase invitePlatformUserUseCase;

  public InvitationController(InvitePlatformUserUseCase invitePlatformUserUseCase) {
    this.invitePlatformUserUseCase = invitePlatformUserUseCase;
  }

  /**
   * Creates a new platform user (201) or refreshes credentials for an existing account (200). Admin
   * only — enforced by {@code @PreAuthorize} at the controller boundary and also by the use-case
   * service (defence in depth).
   */
  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<InvitationResponse>> invite(
      @Valid @RequestBody InvitePlatformUserRequest request) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    UUID callerId = UUID.fromString(auth.getName());

    InvitationResult result =
        invitePlatformUserUseCase.invite(
            new InvitePlatformUserCommand(callerId, true, request.email()));

    InvitationResponse body = new InvitationResponse(request.email(), result.name().toLowerCase());

    if (result == InvitationResult.CREATED) {
      return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(body));
    }
    return ResponseEntity.ok(ApiResponse.success(body));
  }
}
