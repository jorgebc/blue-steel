package com.bluesteel.adapters.in.web.user;

import com.bluesteel.adapters.in.web.ApiResponse;
import com.bluesteel.application.port.in.user.SearchUsersUseCase;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Looks up existing platform users by partial email (typeahead). Admin-or-GM authorization is
 * enforced in the use-case service (D-043, D-064), so no {@code @PreAuthorize} guards this
 * endpoint.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserSearchController {

  private final SearchUsersUseCase searchUsersUseCase;

  public UserSearchController(SearchUsersUseCase searchUsersUseCase) {
    this.searchUsersUseCase = searchUsersUseCase;
  }

  @GetMapping(params = "email")
  public ResponseEntity<ApiResponse<List<UserSearchResponse>>> search(@RequestParam String email) {
    List<UserSearchResponse> results =
        searchUsersUseCase.searchByEmail(email, resolveUserId(), isAdmin()).stream()
            .map(UserSearchResponse::from)
            .toList();
    return ResponseEntity.ok(ApiResponse.success(results));
  }

  private UUID resolveUserId() {
    return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
  }

  private boolean isAdmin() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch("ROLE_ADMIN"::equals);
  }
}
