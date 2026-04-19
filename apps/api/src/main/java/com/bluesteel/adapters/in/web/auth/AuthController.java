package com.bluesteel.adapters.in.web.auth;

import com.bluesteel.adapters.in.web.ApiResponse;
import com.bluesteel.application.model.auth.LoginCommand;
import com.bluesteel.application.model.auth.LoginResult;
import com.bluesteel.application.model.auth.RefreshResult;
import com.bluesteel.application.port.in.auth.LoginUseCase;
import com.bluesteel.application.port.in.auth.LogoutUseCase;
import com.bluesteel.application.port.in.auth.RefreshTokenUseCase;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Auth endpoints: login, refresh, logout (D-059, D-060). */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private static final String REFRESH_TOKEN_COOKIE = "refresh_token";
  private static final String COOKIE_PATH = "/api/v1/auth";
  private static final int THIRTY_DAYS_SECONDS = 30 * 24 * 60 * 60;

  private final LoginUseCase loginUseCase;
  private final RefreshTokenUseCase refreshTokenUseCase;
  private final LogoutUseCase logoutUseCase;

  public AuthController(
      LoginUseCase loginUseCase,
      RefreshTokenUseCase refreshTokenUseCase,
      LogoutUseCase logoutUseCase) {
    this.loginUseCase = loginUseCase;
    this.refreshTokenUseCase = refreshTokenUseCase;
    this.logoutUseCase = logoutUseCase;
  }

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<LoginResponse>> login(
      @Valid @RequestBody LoginRequest request, HttpServletResponse response) {
    LoginResult result = loginUseCase.login(new LoginCommand(request.email(), request.password()));
    setRefreshTokenCookie(response, result.rawRefreshToken(), THIRTY_DAYS_SECONDS);
    return ResponseEntity.ok(
        ApiResponse.success(new LoginResponse(result.accessToken(), result.forcePasswordChange())));
  }

  @PostMapping("/refresh")
  public ResponseEntity<ApiResponse<RefreshResponse>> refresh(
      HttpServletRequest request, HttpServletResponse response) {
    String rawToken = extractRefreshTokenCookie(request);
    RefreshResult result = refreshTokenUseCase.refresh(rawToken);
    setRefreshTokenCookie(response, result.rawRefreshToken(), THIRTY_DAYS_SECONDS);
    return ResponseEntity.ok(ApiResponse.success(new RefreshResponse(result.accessToken())));
  }

  @PostMapping("/logout")
  public ResponseEntity<ApiResponse<Void>> logout(
      HttpServletRequest request, HttpServletResponse response) {
    String rawToken = extractRefreshTokenCookie(request);
    if (rawToken != null) {
      logoutUseCase.logout(rawToken);
    }
    setRefreshTokenCookie(response, "", 0);
    return ResponseEntity.ok(ApiResponse.success(null));
  }

  private String extractRefreshTokenCookie(HttpServletRequest request) {
    if (request.getCookies() == null) return null;
    for (Cookie cookie : request.getCookies()) {
      if (REFRESH_TOKEN_COOKIE.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }

  private void setRefreshTokenCookie(HttpServletResponse response, String value, int maxAge) {
    // httpOnly, SameSite=Strict, Secure, Path=/api/v1/auth (D-059)
    String header =
        String.format(
            "%s=%s; HttpOnly; Secure; SameSite=Strict; Path=%s; Max-Age=%d",
            REFRESH_TOKEN_COOKIE, value, COOKIE_PATH, maxAge);
    response.addHeader("Set-Cookie", header);
  }
}
