package com.bluesteel.adapters.in.web.auth;

import com.bluesteel.adapters.in.web.ApiResponse;
import com.bluesteel.application.model.auth.LoginCommand;
import com.bluesteel.application.model.auth.LoginResult;
import com.bluesteel.application.model.auth.RefreshResult;
import com.bluesteel.application.port.in.auth.LoginUseCase;
import com.bluesteel.application.port.in.auth.LogoutUseCase;
import com.bluesteel.application.port.in.auth.RefreshTokenUseCase;
import com.bluesteel.domain.exception.RefreshTokenException;
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
  private static final int THIRTY_DAYS_SECONDS = 30 * 24 * 60 * 60;

  private final LoginUseCase loginUseCase;
  private final RefreshTokenUseCase refreshTokenUseCase;
  private final LogoutUseCase logoutUseCase;
  private final AuthRateLimiter rateLimiter;
  private final String cookiePath;

  public AuthController(
      LoginUseCase loginUseCase,
      RefreshTokenUseCase refreshTokenUseCase,
      LogoutUseCase logoutUseCase,
      AuthRateLimiter rateLimiter) {
    this.loginUseCase = loginUseCase;
    this.refreshTokenUseCase = refreshTokenUseCase;
    this.logoutUseCase = logoutUseCase;
    this.rateLimiter = rateLimiter;
    this.cookiePath = AuthController.class.getAnnotation(RequestMapping.class).value()[0];
  }

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<LoginResponse>> login(
      @Valid @RequestBody LoginRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse response) {
    rateLimiter.check(clientIp(httpRequest));
    LoginResult result = loginUseCase.login(new LoginCommand(request.email(), request.password()));
    setRefreshTokenCookie(response, result.rawRefreshToken(), THIRTY_DAYS_SECONDS);
    return ResponseEntity.ok(
        ApiResponse.success(new LoginResponse(result.accessToken(), result.forcePasswordChange())));
  }

  @PostMapping("/refresh")
  public ResponseEntity<ApiResponse<RefreshResponse>> refresh(
      HttpServletRequest request, HttpServletResponse response) {
    rateLimiter.check(clientIp(request));
    String rawToken = extractRefreshTokenCookie(request);
    if (rawToken == null) {
      throw new RefreshTokenException("REFRESH_TOKEN_MISSING", "Refresh token cookie required");
    }
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

  /**
   * Resolves the client IP for rate limiting. Behind the reverse proxy the original client is the
   * first hop of {@code X-Forwarded-For}; falls back to the socket address when the header is
   * absent.
   */
  private String clientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",", 2)[0].trim();
    }
    return request.getRemoteAddr();
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
            REFRESH_TOKEN_COOKIE, value, cookiePath, maxAge);
    response.addHeader("Set-Cookie", header);
  }
}
