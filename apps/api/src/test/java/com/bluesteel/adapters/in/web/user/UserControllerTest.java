package com.bluesteel.adapters.in.web.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bluesteel.BlueSteelApplication;
import com.bluesteel.application.model.user.UserProfile;
import com.bluesteel.application.port.in.user.AdminBootstrapUseCase;
import com.bluesteel.application.port.in.user.ChangePasswordUseCase;
import com.bluesteel.application.port.in.user.GetCurrentUserUseCase;
import com.bluesteel.application.port.in.user.InvitePlatformUserUseCase;
import com.bluesteel.domain.exception.InvalidPasswordException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
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
@DisplayName("UserController")
class UserControllerTest {

  private static final String USER_ID = "00000000-0000-0000-0000-000000000001";

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
  @DisplayName("should return 200 with user profile for authenticated user")
  @WithMockUser(username = USER_ID, roles = "USER")
  void getMe_authenticated_returnsProfile() throws Exception {
    UUID userId = UUID.fromString(USER_ID);
    when(getCurrentUserUseCase.getCurrentUser(userId))
        .thenReturn(new UserProfile(userId, "user@example.com", false, true));

    mockMvc
        .perform(get("/api/v1/users/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.email").value("user@example.com"))
        .andExpect(jsonPath("$.data.isAdmin").value(false))
        .andExpect(jsonPath("$.data.forcePasswordChange").value(true));
  }

  @Test
  @DisplayName("should return 401 when user is not authenticated")
  void getMe_unauthenticated_returns401() throws Exception {
    mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("should return 200 on successful password change")
  @WithMockUser(username = USER_ID, roles = "USER")
  void changePassword_valid_returns200() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/users/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "currentPassword": "OldPass1!", "newPassword": "NewPass1!" }
                    """))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("should return 422 when current password is incorrect")
  @WithMockUser(username = USER_ID, roles = "USER")
  void changePassword_wrongCurrentPassword_returns422() throws Exception {
    doThrow(new InvalidPasswordException("Current password is incorrect"))
        .when(changePasswordUseCase)
        .change(any());

    mockMvc
        .perform(
            patch("/api/v1/users/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "currentPassword": "wrongpass", "newPassword": "NewPass1!" }
                    """))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_CURRENT_PASSWORD"));
  }

  @Test
  @DisplayName("should return 400 when newPassword is too short")
  @WithMockUser(username = USER_ID, roles = "USER")
  void changePassword_shortNewPassword_returns400() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/users/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "currentPassword": "OldPass1!", "newPassword": "short" }
                    """))
        .andExpect(status().isBadRequest());
  }
}
