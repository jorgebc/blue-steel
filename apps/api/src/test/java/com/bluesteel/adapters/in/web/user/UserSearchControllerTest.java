package com.bluesteel.adapters.in.web.user;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bluesteel.BlueSteelApplication;
import com.bluesteel.application.model.user.UserProfile;
import com.bluesteel.application.port.in.campaign.CreateCampaignUseCase;
import com.bluesteel.application.port.in.campaign.GetCampaignUseCase;
import com.bluesteel.application.port.in.campaign.InviteCampaignMemberUseCase;
import com.bluesteel.application.port.in.campaign.ListCampaignsUseCase;
import com.bluesteel.application.port.in.user.AdminBootstrapUseCase;
import com.bluesteel.application.port.in.user.ChangePasswordUseCase;
import com.bluesteel.application.port.in.user.GetCurrentUserUseCase;
import com.bluesteel.application.port.in.user.InvitePlatformUserUseCase;
import com.bluesteel.application.port.in.user.SearchUsersUseCase;
import com.bluesteel.domain.exception.UnauthorizedException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
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
@DisplayName("UserSearchController")
class UserSearchControllerTest {

  @MockitoBean private AdminBootstrapUseCase adminBootstrapUseCase;
  @MockitoBean private InvitePlatformUserUseCase invitePlatformUserUseCase;
  @MockitoBean private GetCurrentUserUseCase getCurrentUserUseCase;
  @MockitoBean private ChangePasswordUseCase changePasswordUseCase;
  @MockitoBean private SearchUsersUseCase searchUsersUseCase;
  @MockitoBean private CreateCampaignUseCase createCampaignUseCase;
  @MockitoBean private GetCampaignUseCase getCampaignUseCase;
  @MockitoBean private ListCampaignsUseCase listCampaignsUseCase;
  @MockitoBean private InviteCampaignMemberUseCase inviteCampaignMemberUseCase;

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  private static final UUID CALLER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID FOUND_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

  @BeforeEach
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  @DisplayName("should return 200 with all matching users for a partial email when caller is admin")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "ADMIN")
  void search_adminMatch_returns200() throws Exception {
    when(searchUsersUseCase.searchByEmail("jor", CALLER_ID, true))
        .thenReturn(
            List.of(
                new UserProfile(
                    FOUND_ID, "jorge@example.com", false, false, null, null, "en", "system"),
                new UserProfile(
                    UUID.randomUUID(),
                    "jordan@example.com",
                    false,
                    false,
                    null,
                    null,
                    "en",
                    "system")));

    mockMvc
        .perform(get("/api/v1/users").param("email", "jor"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(FOUND_ID.toString()))
        .andExpect(jsonPath("$.data[0].email").value("jorge@example.com"))
        .andExpect(jsonPath("$.data[1].email").value("jordan@example.com"));
  }

  @Test
  @DisplayName("should return 200 with an empty list when no user matches")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "ADMIN")
  void search_noMatch_returnsEmptyList() throws Exception {
    when(searchUsersUseCase.searchByEmail("none@example.com", CALLER_ID, true))
        .thenReturn(List.of());

    mockMvc
        .perform(get("/api/v1/users").param("email", "none@example.com"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty());
  }

  @Test
  @DisplayName("should return 200 with the matching user when caller is a GM (non-admin)")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void search_gmCaller_returns200() throws Exception {
    when(searchUsersUseCase.searchByEmail("found@example.com", CALLER_ID, false))
        .thenReturn(
            List.of(
                new UserProfile(
                    FOUND_ID, "found@example.com", false, false, null, null, "en", "system")));

    mockMvc
        .perform(get("/api/v1/users").param("email", "found@example.com"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].email").value("found@example.com"));
  }

  @Test
  @DisplayName("should return 403 when caller is neither admin nor a GM")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void search_unauthorizedCaller_returns403() throws Exception {
    when(searchUsersUseCase.searchByEmail("found@example.com", CALLER_ID, false))
        .thenThrow(new UnauthorizedException("not allowed"));

    mockMvc
        .perform(get("/api/v1/users").param("email", "found@example.com"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("should return 401 when caller is not authenticated")
  void search_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(get("/api/v1/users").param("email", "found@example.com"))
        .andExpect(status().isUnauthorized());
  }
}
