package com.bluesteel.adapters.in.web.annotation;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bluesteel.BlueSteelApplication;
import com.bluesteel.application.model.annotation.AnnotationView;
import com.bluesteel.application.port.in.annotation.CreateAnnotationUseCase;
import com.bluesteel.application.port.in.annotation.DeleteAnnotationUseCase;
import com.bluesteel.application.port.in.annotation.ListAnnotationsUseCase;
import com.bluesteel.application.port.in.campaign.InviteCampaignMemberUseCase;
import com.bluesteel.application.port.in.health.CheckHealthUseCase;
import com.bluesteel.application.port.in.user.AdminBootstrapUseCase;
import com.bluesteel.application.port.in.user.ChangePasswordUseCase;
import com.bluesteel.application.port.in.user.GetCurrentUserUseCase;
import com.bluesteel.application.port.in.user.InvitePlatformUserUseCase;
import com.bluesteel.domain.exception.AnnotationNotFoundException;
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
@DisplayName("AnnotationController")
class AnnotationControllerTest {

  @MockitoBean private CreateAnnotationUseCase createAnnotationUseCase;
  @MockitoBean private ListAnnotationsUseCase listAnnotationsUseCase;
  @MockitoBean private DeleteAnnotationUseCase deleteAnnotationUseCase;

  @MockitoBean private CheckHealthUseCase checkHealthUseCase;
  @MockitoBean private AdminBootstrapUseCase adminBootstrapUseCase;
  @MockitoBean private InvitePlatformUserUseCase invitePlatformUserUseCase;
  @MockitoBean private InviteCampaignMemberUseCase inviteCampaignMemberUseCase;
  @MockitoBean private GetCurrentUserUseCase getCurrentUserUseCase;
  @MockitoBean private ChangePasswordUseCase changePasswordUseCase;

  @Autowired private WebApplicationContext context;
  private MockMvc mockMvc;

  private static final UUID CAMPAIGN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID ENTITY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID ANNOTATION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID AUTHOR_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
  private static final String CALLER = "44444444-4444-4444-4444-444444444444";
  private static final UUID CALLER_ID = UUID.fromString(CALLER);

  @BeforeEach
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  // -------------------------------------------------------------------------
  // POST /api/v1/campaigns/{id}/annotations
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("should return 201 with annotation response on successful create")
  @WithMockUser(username = CALLER, roles = "USER")
  void createAnnotation_validRequest_returns201() throws Exception {
    AnnotationView view =
        new AnnotationView(
            ANNOTATION_ID,
            CAMPAIGN_ID,
            ENTITY_ID,
            "actor",
            AUTHOR_ID,
            "Aldric is suspicious",
            Instant.parse("2026-06-01T10:00:00Z"));
    when(createAnnotationUseCase.create(
            new com.bluesteel.application.model.annotation.CreateAnnotationCommand(
                CAMPAIGN_ID, ENTITY_ID, "actor", "Aldric is suspicious", CALLER_ID)))
        .thenReturn(view);

    mockMvc
        .perform(
            post("/api/v1/campaigns/{id}/annotations", CAMPAIGN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "entityType": "actor",
                      "entityId": "%s",
                      "content": "Aldric is suspicious"
                    }
                    """
                        .formatted(ENTITY_ID)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.id").value(ANNOTATION_ID.toString()))
        .andExpect(jsonPath("$.data.entityType").value("actor"))
        .andExpect(jsonPath("$.data.content").value("Aldric is suspicious"))
        .andExpect(jsonPath("$.data.authorId").value(AUTHOR_ID.toString()));
  }

  @Test
  @DisplayName("should return 400 when content is blank")
  @WithMockUser(username = CALLER, roles = "USER")
  void createAnnotation_blankContent_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/campaigns/{id}/annotations", CAMPAIGN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "entityType": "actor",
                      "entityId": "%s",
                      "content": ""
                    }
                    """
                        .formatted(ENTITY_ID)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[0].field").value("content"));
  }

  @Test
  @DisplayName("should return 400 when entityType is invalid")
  @WithMockUser(username = CALLER, roles = "USER")
  void createAnnotation_invalidEntityType_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/campaigns/{id}/annotations", CAMPAIGN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "entityType": "invalid_type",
                      "entityId": "%s",
                      "content": "Some note"
                    }
                    """
                        .formatted(ENTITY_ID)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[0].field").value("entityType"));
  }

  @Test
  @DisplayName("should return 403 when caller is not a campaign member")
  @WithMockUser(username = CALLER, roles = "USER")
  void createAnnotation_nonMember_returns403() throws Exception {
    when(createAnnotationUseCase.create(
            new com.bluesteel.application.model.annotation.CreateAnnotationCommand(
                CAMPAIGN_ID, ENTITY_ID, "actor", "Note", CALLER_ID)))
        .thenThrow(new UnauthorizedException("Caller is not a member of this campaign"));

    mockMvc
        .perform(
            post("/api/v1/campaigns/{id}/annotations", CAMPAIGN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "entityType": "actor",
                      "entityId": "%s",
                      "content": "Note"
                    }
                    """
                        .formatted(ENTITY_ID)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errors[0].code").value("FORBIDDEN"));
  }

  @Test
  @DisplayName("should return 401 when unauthenticated")
  void createAnnotation_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/campaigns/{id}/annotations", CAMPAIGN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"entityType":"actor","entityId":"%s","content":"Note"}
                    """
                        .formatted(ENTITY_ID)))
        .andExpect(status().isUnauthorized());
  }

  // -------------------------------------------------------------------------
  // GET /api/v1/campaigns/{id}/annotations?entityType=&entityId=
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("should return 200 with annotation list for a valid entity query")
  @WithMockUser(username = CALLER, roles = "USER")
  void listAnnotations_validParams_returns200() throws Exception {
    AnnotationView view =
        new AnnotationView(
            ANNOTATION_ID, CAMPAIGN_ID, ENTITY_ID, "actor", AUTHOR_ID, "Note", Instant.now());
    when(listAnnotationsUseCase.list(CAMPAIGN_ID, "actor", ENTITY_ID, CALLER_ID))
        .thenReturn(List.of(view));

    mockMvc
        .perform(
            get("/api/v1/campaigns/{id}/annotations", CAMPAIGN_ID)
                .param("entityType", "actor")
                .param("entityId", ENTITY_ID.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(ANNOTATION_ID.toString()))
        .andExpect(jsonPath("$.data[0].content").value("Note"));
  }

  // -------------------------------------------------------------------------
  // DELETE /api/v1/campaigns/{id}/annotations/{aid}
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("should return 200 with null data on successful delete")
  @WithMockUser(username = CALLER, roles = "USER")
  void deleteAnnotation_authorOrGm_returns200() throws Exception {
    mockMvc
        .perform(delete("/api/v1/campaigns/{id}/annotations/{aid}", CAMPAIGN_ID, ANNOTATION_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").doesNotExist());

    verify(deleteAnnotationUseCase).delete(CAMPAIGN_ID, ANNOTATION_ID, CALLER_ID);
  }

  @Test
  @DisplayName("should return 404 with ANNOTATION_NOT_FOUND when annotation does not exist")
  @WithMockUser(username = CALLER, roles = "USER")
  void deleteAnnotation_missing_returns404() throws Exception {
    doThrow(new AnnotationNotFoundException("Annotation not found: " + ANNOTATION_ID))
        .when(deleteAnnotationUseCase)
        .delete(CAMPAIGN_ID, ANNOTATION_ID, CALLER_ID);

    mockMvc
        .perform(delete("/api/v1/campaigns/{id}/annotations/{aid}", CAMPAIGN_ID, ANNOTATION_ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errors[0].code").value("ANNOTATION_NOT_FOUND"));
  }

  @Test
  @DisplayName("should return 403 when caller is neither author nor GM")
  @WithMockUser(username = CALLER, roles = "USER")
  void deleteAnnotation_unauthorized_returns403() throws Exception {
    doThrow(
            new UnauthorizedException(
                "Only the annotation's author or a campaign GM may delete it"))
        .when(deleteAnnotationUseCase)
        .delete(CAMPAIGN_ID, ANNOTATION_ID, CALLER_ID);

    mockMvc
        .perform(delete("/api/v1/campaigns/{id}/annotations/{aid}", CAMPAIGN_ID, ANNOTATION_ID))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errors[0].code").value("FORBIDDEN"));
  }
}
