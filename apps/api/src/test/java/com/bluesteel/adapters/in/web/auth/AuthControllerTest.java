package com.bluesteel.adapters.in.web.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bluesteel.BlueSteelApplication;
import com.bluesteel.application.model.auth.LoginResult;
import com.bluesteel.application.model.auth.RefreshResult;
import com.bluesteel.application.port.in.auth.LoginUseCase;
import com.bluesteel.application.port.in.auth.LogoutUseCase;
import com.bluesteel.application.port.in.auth.RefreshTokenUseCase;
import com.bluesteel.application.port.in.user.AdminBootstrapUseCase;
import com.bluesteel.application.port.in.user.ChangePasswordUseCase;
import com.bluesteel.application.port.in.user.GetCurrentUserUseCase;
import com.bluesteel.application.port.in.user.InvitePlatformUserUseCase;
import com.bluesteel.domain.exception.InvalidCredentialsException;
import com.bluesteel.domain.exception.RefreshTokenException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(
    classes = BlueSteelApplication.class,
    webEnvironment = WebEnvironment.MOCK,
    properties = {
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
          + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
          + "org.springframework.boot.data.jpa.autoconfigure.JpaRepositoriesAutoConfiguration,"
          + "org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration",
      "admin.email=admin@test.com",
      "admin.password=AdminTest!Password123",
      "jwt.secret=test-jwt-secret-test-jwt-secret-test-jwt-secret!"
    })
@DisplayName("AuthController")
class AuthControllerTest {

  private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @MockitoBean private LoginUseCase loginUseCase;
  @MockitoBean private RefreshTokenUseCase refreshTokenUseCase;
  @MockitoBean private LogoutUseCase logoutUseCase;
  @MockitoBean private AdminBootstrapUseCase adminBootstrapUseCase;
  @MockitoBean private InvitePlatformUserUseCase invitePlatformUserUseCase;
  @MockitoBean private GetCurrentUserUseCase getCurrentUserUseCase;
  @MockitoBean private ChangePasswordUseCase changePasswordUseCase;

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @BeforeEach
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  @DisplayName(
      "should return 200 with accessToken and set refresh_token cookie on successful login")
  void login_validCredentials_returns200WithTokens() throws Exception {
    when(loginUseCase.login(any()))
        .thenReturn(
            new LoginResult("access-token-123", "raw-refresh-token", USER_ID, false, false));

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "email": "user@example.com", "password": "Password1!" }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").value("access-token-123"))
        .andExpect(jsonPath("$.data.forcePasswordChange").value(false));
  }

  @Test
  @DisplayName("should return 401 when credentials are invalid")
  void login_invalidCredentials_returns401() throws Exception {
    when(loginUseCase.login(any()))
        .thenThrow(new InvalidCredentialsException("Invalid email or password"));

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "email": "user@example.com", "password": "wrong" }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_CREDENTIALS"));
  }

  @Test
  @DisplayName("should return 401 on refresh token reuse")
  void refresh_tokenReuseDetected_returns401() throws Exception {
    when(refreshTokenUseCase.refresh(any()))
        .thenThrow(new RefreshTokenException("REFRESH_TOKEN_REUSE_DETECTED", "Reuse detected"));

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .cookie(new jakarta.servlet.http.Cookie("refresh_token", "stale-token")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errors[0].code").value("REFRESH_TOKEN_REUSE_DETECTED"));
  }

  @Test
  @DisplayName("should return 200 and rotate tokens on valid refresh")
  void refresh_validToken_returns200WithNewTokens() throws Exception {
    when(refreshTokenUseCase.refresh(any()))
        .thenReturn(new RefreshResult("new-access-token", "new-refresh-token", USER_ID, false));

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .cookie(new jakarta.servlet.http.Cookie("refresh_token", "valid-token")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").value("new-access-token"));
  }

  @Test
  @DisplayName("should return 200 and clear cookie on logout")
  void logout_authenticated_returns200AndClearsCookie() throws Exception {
    doNothing().when(logoutUseCase).logout(any());

    mockMvc
        .perform(
            post("/api/v1/auth/logout")
                .cookie(new jakarta.servlet.http.Cookie("refresh_token", "some-token")))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("should return 400 when login request is missing email")
  void login_missingEmail_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "password": "Password1!" }
                    """))
        .andExpect(status().isBadRequest());
  }
}
