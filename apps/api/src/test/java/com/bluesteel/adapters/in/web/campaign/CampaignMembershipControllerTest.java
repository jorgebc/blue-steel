package com.bluesteel.adapters.in.web.campaign;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bluesteel.BlueSteelApplication;
import com.bluesteel.application.model.campaign.ChangeMemberRoleCommand;
import com.bluesteel.application.model.campaign.InviteCampaignMemberCommand;
import com.bluesteel.application.model.campaign.RemoveMemberCommand;
import com.bluesteel.application.port.in.campaign.ChangeMemberRoleUseCase;
import com.bluesteel.application.port.in.campaign.CreateCampaignUseCase;
import com.bluesteel.application.port.in.campaign.GetCampaignUseCase;
import com.bluesteel.application.port.in.campaign.InviteCampaignMemberUseCase;
import com.bluesteel.application.port.in.campaign.ListCampaignsUseCase;
import com.bluesteel.application.port.in.campaign.RemoveMemberUseCase;
import com.bluesteel.application.port.in.user.AdminBootstrapUseCase;
import com.bluesteel.application.port.in.user.ChangePasswordUseCase;
import com.bluesteel.application.port.in.user.GetCurrentUserUseCase;
import com.bluesteel.application.port.in.user.InvitePlatformUserUseCase;
import com.bluesteel.application.port.in.user.SearchUsersUseCase;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.AlreadyCampaignMemberException;
import com.bluesteel.domain.exception.CannotRemoveGmException;
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
@DisplayName("CampaignMembershipController")
class CampaignMembershipControllerTest {

  @MockitoBean private AdminBootstrapUseCase adminBootstrapUseCase;
  @MockitoBean private InvitePlatformUserUseCase invitePlatformUserUseCase;
  @MockitoBean private GetCurrentUserUseCase getCurrentUserUseCase;
  @MockitoBean private ChangePasswordUseCase changePasswordUseCase;
  @MockitoBean private SearchUsersUseCase searchUsersUseCase;
  @MockitoBean private CreateCampaignUseCase createCampaignUseCase;
  @MockitoBean private GetCampaignUseCase getCampaignUseCase;
  @MockitoBean private ListCampaignsUseCase listCampaignsUseCase;
  @MockitoBean private InviteCampaignMemberUseCase inviteCampaignMemberUseCase;
  @MockitoBean private ChangeMemberRoleUseCase changeMemberRoleUseCase;
  @MockitoBean private RemoveMemberUseCase removeMemberUseCase;

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  private static final UUID CAMPAIGN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID TARGET_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID CALLER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @BeforeEach
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  @DisplayName("should return 201 when inviting a new user as GM")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void invite_newUser_returns201() throws Exception {
    when(inviteCampaignMemberUseCase.invite(
            new InviteCampaignMemberCommand(
                CAMPAIGN_ID, CALLER_ID, "new@example.com", CampaignRole.EDITOR)))
        .thenReturn(true);

    mockMvc
        .perform(
            post("/api/v1/campaigns/{id}/invitations", CAMPAIGN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"email\": \"new@example.com\", \"role\": \"EDITOR\" }"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.email").value("new@example.com"))
        .andExpect(jsonPath("$.data.role").value("editor"))
        .andExpect(jsonPath("$.data.created").value(true));
  }

  @Test
  @DisplayName("should return 200 when inviting an existing user as GM")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void invite_existingUser_returns200() throws Exception {
    when(inviteCampaignMemberUseCase.invite(
            new InviteCampaignMemberCommand(
                CAMPAIGN_ID, CALLER_ID, "existing@example.com", CampaignRole.PLAYER)))
        .thenReturn(false);

    mockMvc
        .perform(
            post("/api/v1/campaigns/{id}/invitations", CAMPAIGN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"email\": \"existing@example.com\", \"role\": \"PLAYER\" }"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.created").value(false));
  }

  @Test
  @DisplayName("should return 409 when the invited user is already a member")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void invite_alreadyMember_returns409() throws Exception {
    when(inviteCampaignMemberUseCase.invite(
            new InviteCampaignMemberCommand(
                CAMPAIGN_ID, CALLER_ID, "member@example.com", CampaignRole.PLAYER)))
        .thenThrow(new AlreadyCampaignMemberException(CAMPAIGN_ID, TARGET_ID));

    mockMvc
        .perform(
            post("/api/v1/campaigns/{id}/invitations", CAMPAIGN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"email\": \"member@example.com\", \"role\": \"PLAYER\" }"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("ALREADY_CAMPAIGN_MEMBER"));
  }

  @Test
  @DisplayName("should return 400 when inviting with role gm")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void invite_gmRole_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/campaigns/{id}/invitations", CAMPAIGN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"email\": \"new@example.com\", \"role\": \"GM\" }"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("should return 400 when email is not a valid address")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void invite_badEmail_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/campaigns/{id}/invitations", CAMPAIGN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"email\": \"not-an-email\", \"role\": \"EDITOR\" }"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("should return 200 when GM changes a member's role")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void changeRole_valid_returns200() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/campaigns/{id}/members/{uid}", CAMPAIGN_ID, TARGET_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"role\": \"EDITOR\" }"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("should return 422 when changing the role of a GM")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void changeRole_targetIsGm_returns422() throws Exception {
    org.mockito.Mockito.doThrow(new CannotRemoveGmException(TARGET_ID))
        .when(changeMemberRoleUseCase)
        .change(
            new ChangeMemberRoleCommand(CAMPAIGN_ID, CALLER_ID, TARGET_ID, CampaignRole.PLAYER));

    mockMvc
        .perform(
            patch("/api/v1/campaigns/{id}/members/{uid}", CAMPAIGN_ID, TARGET_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"role\": \"PLAYER\" }"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("CANNOT_REMOVE_GM"));
  }

  @Test
  @DisplayName("should return 200 when GM removes a member")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void remove_valid_returns200() throws Exception {
    mockMvc
        .perform(delete("/api/v1/campaigns/{id}/members/{uid}", CAMPAIGN_ID, TARGET_ID))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("should return 422 when removing a GM")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void remove_targetIsGm_returns422() throws Exception {
    org.mockito.Mockito.doThrow(new CannotRemoveGmException(TARGET_ID))
        .when(removeMemberUseCase)
        .remove(new RemoveMemberCommand(CAMPAIGN_ID, CALLER_ID, TARGET_ID));

    mockMvc
        .perform(delete("/api/v1/campaigns/{id}/members/{uid}", CAMPAIGN_ID, TARGET_ID))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("CANNOT_REMOVE_GM"));
  }

  @Test
  @DisplayName("should return 401 when caller is not authenticated")
  void invite_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/campaigns/{id}/invitations", CAMPAIGN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"email\": \"new@example.com\", \"role\": \"EDITOR\" }"))
        .andExpect(status().isUnauthorized());
  }
}
