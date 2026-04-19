package com.bluesteel.adapters.in.web.user;

import com.bluesteel.adapters.in.web.ApiResponse;
import com.bluesteel.application.model.user.ChangePasswordCommand;
import com.bluesteel.application.model.user.UserProfile;
import com.bluesteel.application.port.in.user.ChangePasswordUseCase;
import com.bluesteel.application.port.in.user.GetCurrentUserUseCase;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Handles authenticated user profile and password management. */
@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

  private final GetCurrentUserUseCase getCurrentUserUseCase;
  private final ChangePasswordUseCase changePasswordUseCase;

  public UserController(
      GetCurrentUserUseCase getCurrentUserUseCase, ChangePasswordUseCase changePasswordUseCase) {
    this.getCurrentUserUseCase = getCurrentUserUseCase;
    this.changePasswordUseCase = changePasswordUseCase;
  }

  /** Returns the authenticated user's profile. */
  @GetMapping
  public ResponseEntity<ApiResponse<UserMeResponse>> getMe() {
    UUID userId = resolveUserId();
    UserProfile profile = getCurrentUserUseCase.getCurrentUser(userId);
    return ResponseEntity.ok(
        ApiResponse.success(
            new UserMeResponse(
                profile.id(), profile.email(), profile.isAdmin(), profile.forcePasswordChange())));
  }

  /** Changes the authenticated user's password and clears the force_password_change flag. */
  @PatchMapping("/password")
  public ResponseEntity<ApiResponse<Void>> changePassword(
      @Valid @RequestBody ChangePasswordRequest request) {
    UUID userId = resolveUserId();
    changePasswordUseCase.change(
        new ChangePasswordCommand(userId, request.currentPassword(), request.newPassword()));
    return ResponseEntity.ok(ApiResponse.success(null));
  }

  private UUID resolveUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return UUID.fromString(auth.getName());
  }
}
