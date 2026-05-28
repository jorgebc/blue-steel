package com.bluesteel.adapters.in.web.campaign;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bluesteel.BlueSteelApplication;
import com.bluesteel.application.model.campaign.CampaignView;
import com.bluesteel.application.model.campaign.CreateCampaignCommand;
import com.bluesteel.application.port.in.campaign.CreateCampaignUseCase;
import com.bluesteel.application.port.in.campaign.GetCampaignUseCase;
import com.bluesteel.application.port.in.campaign.ListCampaignsUseCase;
import com.bluesteel.application.port.in.user.AdminBootstrapUseCase;
import com.bluesteel.application.port.in.user.ChangePasswordUseCase;
import com.bluesteel.application.port.in.user.GetCurrentUserUseCase;
import com.bluesteel.application.port.in.user.InvitePlatformUserUseCase;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.CampaignNotFoundException;
import com.bluesteel.domain.exception.UnauthorizedException;
import java.time.Instant;
import java.util.List;
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
@DisplayName("CampaignController")
class CampaignControllerTest {

  @MockitoBean private AdminBootstrapUseCase adminBootstrapUseCase;
  @MockitoBean private InvitePlatformUserUseCase invitePlatformUserUseCase;
  @MockitoBean private GetCurrentUserUseCase getCurrentUserUseCase;
  @MockitoBean private ChangePasswordUseCase changePasswordUseCase;
  @MockitoBean private CreateCampaignUseCase createCampaignUseCase;
  @MockitoBean private GetCampaignUseCase getCampaignUseCase;
  @MockitoBean private ListCampaignsUseCase listCampaignsUseCase;

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  private static final UUID CAMPAIGN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID GM_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID CALLER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final Instant NOW = Instant.parse("2025-01-01T00:00:00Z");

  @BeforeEach
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  @DisplayName("should return 201 when admin creates a campaign")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "ADMIN")
  void create_adminUser_returns201() throws Exception {
    CampaignView view =
        new CampaignView(CAMPAIGN_ID, "Dragon Keep", CALLER_ID, NOW, CampaignRole.GM);
    when(createCampaignUseCase.create(
            new CreateCampaignCommand(CALLER_ID, true, "Dragon Keep", GM_USER_ID)))
        .thenReturn(view);

    mockMvc
        .perform(
            post("/api/v1/campaigns")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "name": "Dragon Keep", "gmUserId": "22222222-2222-2222-2222-222222222222" }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.name").value("Dragon Keep"))
        .andExpect(jsonPath("$.data.role").value("gm"));
  }

  @Test
  @DisplayName("should return 403 when non-admin tries to create a campaign")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void create_nonAdmin_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/campaigns")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "name": "Dragon Keep", "gmUserId": "22222222-2222-2222-2222-222222222222" }
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("should return 400 when campaign name is blank")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "ADMIN")
  void create_blankName_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/campaigns")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "name": "", "gmUserId": "22222222-2222-2222-2222-222222222222" }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("should return 400 when gmUserId is missing")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "ADMIN")
  void create_missingGmUserId_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/campaigns")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "name": "Dragon Keep" }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("should return 200 with list of campaigns for authenticated user")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void list_authenticatedUser_returns200() throws Exception {
    CampaignView view =
        new CampaignView(CAMPAIGN_ID, "Dragon Keep", CALLER_ID, NOW, CampaignRole.PLAYER);
    when(listCampaignsUseCase.list(CALLER_ID, false)).thenReturn(List.of(view));

    mockMvc
        .perform(get("/api/v1/campaigns"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].name").value("Dragon Keep"))
        .andExpect(jsonPath("$.data[0].role").value("player"));
  }

  @Test
  @DisplayName("should return 200 with campaign details for a member")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void get_member_returns200() throws Exception {
    CampaignView view =
        new CampaignView(CAMPAIGN_ID, "Dragon Keep", CALLER_ID, NOW, CampaignRole.GM);
    when(getCampaignUseCase.get(CAMPAIGN_ID, CALLER_ID, false)).thenReturn(view);

    mockMvc
        .perform(get("/api/v1/campaigns/{id}", CAMPAIGN_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(CAMPAIGN_ID.toString()))
        .andExpect(jsonPath("$.data.role").value("gm"));
  }

  @Test
  @DisplayName("should return 404 when campaign does not exist")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void get_campaignNotFound_returns404() throws Exception {
    when(getCampaignUseCase.get(CAMPAIGN_ID, CALLER_ID, false))
        .thenThrow(new CampaignNotFoundException(CAMPAIGN_ID));

    mockMvc
        .perform(get("/api/v1/campaigns/{id}", CAMPAIGN_ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errors[0].code").value("CAMPAIGN_NOT_FOUND"));
  }

  @Test
  @DisplayName("should return 403 when caller is not a member of the campaign")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void get_nonMember_returns403() throws Exception {
    when(getCampaignUseCase.get(CAMPAIGN_ID, CALLER_ID, false))
        .thenThrow(new UnauthorizedException("Not a member"));

    mockMvc.perform(get("/api/v1/campaigns/{id}", CAMPAIGN_ID)).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("should return 401 when caller is not authenticated")
  void get_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(get("/api/v1/campaigns/{id}", CAMPAIGN_ID))
        .andExpect(status().isUnauthorized());
  }
}
