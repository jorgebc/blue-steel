package com.bluesteel.adapters.in.web.campaign;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bluesteel.BlueSteelApplication;
import com.bluesteel.application.model.campaign.ArchivedCampaign;
import com.bluesteel.application.model.campaign.CampaignArchive;
import com.bluesteel.application.port.in.campaign.ChangeMemberRoleUseCase;
import com.bluesteel.application.port.in.campaign.CreateCampaignUseCase;
import com.bluesteel.application.port.in.campaign.ExportCampaignUseCase;
import com.bluesteel.application.port.in.campaign.GetCampaignUseCase;
import com.bluesteel.application.port.in.campaign.InviteCampaignMemberUseCase;
import com.bluesteel.application.port.in.campaign.ListCampaignMembersUseCase;
import com.bluesteel.application.port.in.campaign.ListCampaignsUseCase;
import com.bluesteel.application.port.in.campaign.RemoveMemberUseCase;
import com.bluesteel.application.port.in.user.AdminBootstrapUseCase;
import com.bluesteel.application.port.in.user.ChangePasswordUseCase;
import com.bluesteel.application.port.in.user.GetCurrentUserUseCase;
import com.bluesteel.application.port.in.user.InvitePlatformUserUseCase;
import com.bluesteel.application.port.in.user.SearchUsersUseCase;
import com.bluesteel.domain.exception.CampaignNotFoundException;
import com.bluesteel.domain.exception.ExportTooLargeException;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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
@DisplayName("CampaignExportController")
class CampaignExportControllerTest {

  @MockitoBean private ExportCampaignUseCase exportCampaignUseCase;

  // Remaining use cases mocked so the full web context loads without a database.
  @MockitoBean private AdminBootstrapUseCase adminBootstrapUseCase;
  @MockitoBean private InvitePlatformUserUseCase invitePlatformUserUseCase;
  @MockitoBean private GetCurrentUserUseCase getCurrentUserUseCase;
  @MockitoBean private ChangePasswordUseCase changePasswordUseCase;
  @MockitoBean private SearchUsersUseCase searchUsersUseCase;
  @MockitoBean private CreateCampaignUseCase createCampaignUseCase;
  @MockitoBean private GetCampaignUseCase getCampaignUseCase;
  @MockitoBean private ListCampaignsUseCase listCampaignsUseCase;
  @MockitoBean private InviteCampaignMemberUseCase inviteCampaignMemberUseCase;
  @MockitoBean private ListCampaignMembersUseCase listCampaignMembersUseCase;
  @MockitoBean private ChangeMemberRoleUseCase changeMemberRoleUseCase;
  @MockitoBean private RemoveMemberUseCase removeMemberUseCase;

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  private static final UUID CAMPAIGN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID CALLER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @BeforeEach
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  private CampaignArchive archive() {
    return new CampaignArchive(
        "1",
        Instant.parse("2026-06-26T12:00:00Z"),
        new ArchivedCampaign(CAMPAIGN_ID, "Lost Mines", CALLER_ID, Instant.now(), "en"),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  @Test
  @DisplayName("should stream the raw archive as a JSON attachment for the GM")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void export_gm_streamsAttachment() throws Exception {
    when(exportCampaignUseCase.export(CAMPAIGN_ID, CALLER_ID, false)).thenReturn(archive());

    MvcResult result =
        mockMvc
            .perform(get("/api/v1/campaigns/{id}/export", CAMPAIGN_ID))
            .andExpect(request().asyncStarted())
            .andReturn();

    mockMvc
        .perform(asyncDispatch(result))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string("Content-Disposition", "attachment; filename=\"lost-mines-export.json\""))
        .andExpect(content().contentTypeCompatibleWith("application/json"))
        // Raw archive, not wrapped in the { data, meta, errors } envelope.
        .andExpect(jsonPath("$.schemaVersion").value("1"))
        .andExpect(jsonPath("$.campaign.name").value("Lost Mines"));
  }

  @Test
  @DisplayName("should stream the archive for an admin (callerIsAdmin=true)")
  @WithMockUser(
      username = "00000000-0000-0000-0000-000000000001",
      roles = {"USER", "ADMIN"})
  void export_admin_streamsAttachment() throws Exception {
    when(exportCampaignUseCase.export(CAMPAIGN_ID, CALLER_ID, true)).thenReturn(archive());

    MvcResult result =
        mockMvc
            .perform(get("/api/v1/campaigns/{id}/export", CAMPAIGN_ID))
            .andExpect(request().asyncStarted())
            .andReturn();

    mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("should return 403 when a non-GM member is rejected by the use case")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void export_player_returns403() throws Exception {
    when(exportCampaignUseCase.export(CAMPAIGN_ID, CALLER_ID, false))
        .thenThrow(new UnauthorizedException("Only the GM or an admin may export this campaign"));

    mockMvc
        .perform(get("/api/v1/campaigns/{id}/export", CAMPAIGN_ID))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errors[0].code").value("FORBIDDEN"));
  }

  @Test
  @DisplayName("should return 404 when the campaign does not exist")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void export_unknownCampaign_returns404() throws Exception {
    when(exportCampaignUseCase.export(CAMPAIGN_ID, CALLER_ID, false))
        .thenThrow(new CampaignNotFoundException(CAMPAIGN_ID));

    mockMvc
        .perform(get("/api/v1/campaigns/{id}/export", CAMPAIGN_ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errors[0].code").value("CAMPAIGN_NOT_FOUND"));
  }

  @Test
  @DisplayName("should return 422 EXPORT_TOO_LARGE before any body is written")
  @WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = "USER")
  void export_overCap_returns422() throws Exception {
    when(exportCampaignUseCase.export(CAMPAIGN_ID, CALLER_ID, false))
        .thenThrow(
            new ExportTooLargeException(
                "Campaign has 9999 entities, exceeding the export limit of 2000"));

    mockMvc
        .perform(get("/api/v1/campaigns/{id}/export", CAMPAIGN_ID))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EXPORT_TOO_LARGE"));
  }

  @Test
  @DisplayName("should return 401 when the caller is not authenticated")
  void export_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(get("/api/v1/campaigns/{id}/export", CAMPAIGN_ID))
        .andExpect(status().isUnauthorized());
  }
}
