package com.bluesteel.adapters.in.web.invitation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bluesteel.BlueSteelApplication;
import com.bluesteel.application.model.user.InvitationResult;
import com.bluesteel.application.port.in.user.AdminBootstrapUseCase;
import com.bluesteel.application.port.in.user.ChangePasswordUseCase;
import com.bluesteel.application.port.in.user.GetCurrentUserUseCase;
import com.bluesteel.application.port.in.user.InvitePlatformUserUseCase;
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
@DisplayName("InvitationController")
class InvitationControllerTest {

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
  @DisplayName("should return 201 when a new user account is created")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "ADMIN")
  void invite_newEmail_returns201() throws Exception {
    when(invitePlatformUserUseCase.invite(any())).thenReturn(InvitationResult.CREATED);

    mockMvc
        .perform(
            post("/api/v1/invitations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "email": "new@example.com" }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.email").value("new@example.com"))
        .andExpect(jsonPath("$.data.status").value("created"));
  }

  @Test
  @DisplayName("should return 200 when existing account credentials are refreshed")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "ADMIN")
  void invite_existingEmail_returns200() throws Exception {
    when(invitePlatformUserUseCase.invite(any())).thenReturn(InvitationResult.REFRESHED);

    mockMvc
        .perform(
            post("/api/v1/invitations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "email": "existing@example.com" }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("refreshed"));
  }

  @Test
  @DisplayName("should return 400 when email is missing")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "ADMIN")
  void invite_missingEmail_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/invitations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "email": "" }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("should return 401 when caller is not authenticated")
  void invite_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/invitations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "email": "someone@example.com" }
                    """))
        .andExpect(status().isUnauthorized());
  }
}
