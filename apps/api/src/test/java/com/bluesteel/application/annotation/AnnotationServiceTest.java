package com.bluesteel.application.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluesteel.application.model.annotation.AnnotationView;
import com.bluesteel.application.model.annotation.CreateAnnotationCommand;
import com.bluesteel.application.port.out.annotation.AnnotationRepository;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.application.service.annotation.AnnotationService;
import com.bluesteel.domain.annotation.Annotation;
import com.bluesteel.domain.annotation.AnnotationEntityType;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.AnnotationNotFoundException;
import com.bluesteel.domain.exception.UnauthorizedException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnnotationService")
class AnnotationServiceTest {

  @Mock private AnnotationRepository annotationRepository;
  @Mock private CampaignMembershipPort membershipPort;

  private AnnotationService sut;

  private static final UUID CAMPAIGN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID ENTITY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID AUTHOR_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID GM_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
  private static final UUID ANNOTATION_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

  @BeforeEach
  void setUp() {
    sut = new AnnotationService(annotationRepository, membershipPort);
  }

  // -------------------------------------------------------------------------
  // Create
  // -------------------------------------------------------------------------

  @Nested
  @DisplayName("create")
  class Create {

    @Test
    @DisplayName("should create and return annotation when caller is a campaign member")
    void create_member_savesAndReturnsView() {
      when(membershipPort.resolveRole(CAMPAIGN_ID, AUTHOR_ID))
          .thenReturn(Optional.of(CampaignRole.PLAYER));
      CreateAnnotationCommand cmd =
          new CreateAnnotationCommand(CAMPAIGN_ID, ENTITY_ID, "actor", "Great scene", AUTHOR_ID);
      Annotation stub = stubAnnotation(AUTHOR_ID, "Great scene");
      ArgumentCaptor<Annotation> captor = forClass(Annotation.class);
      when(annotationRepository.save(captor.capture())).thenReturn(stub);

      AnnotationView view = sut.create(cmd);

      assertThat(captor.getValue().content()).isEqualTo("Great scene");
      assertThat(captor.getValue().entityType()).isEqualTo(AnnotationEntityType.actor);
      assertThat(view.content()).isEqualTo("Great scene");
      assertThat(view.authorId()).isEqualTo(AUTHOR_ID);
    }

    @Test
    @DisplayName("should throw UnauthorizedException when caller is not a member")
    void create_nonMember_throwsUnauthorized() {
      when(membershipPort.resolveRole(CAMPAIGN_ID, AUTHOR_ID)).thenReturn(Optional.empty());
      CreateAnnotationCommand cmd =
          new CreateAnnotationCommand(CAMPAIGN_ID, ENTITY_ID, "actor", "note", AUTHOR_ID);

      assertThatThrownBy(() -> sut.create(cmd)).isInstanceOf(UnauthorizedException.class);
      verify(annotationRepository, never()).save(any(Annotation.class));
    }
  }

  // -------------------------------------------------------------------------
  // List
  // -------------------------------------------------------------------------

  @Nested
  @DisplayName("list")
  class ListAnnotations {

    @Test
    @DisplayName("should return annotations for a member caller")
    void list_member_returnsAnnotations() {
      when(membershipPort.resolveRole(CAMPAIGN_ID, AUTHOR_ID))
          .thenReturn(Optional.of(CampaignRole.PLAYER));
      Annotation a = stubAnnotation(AUTHOR_ID, "Note");
      when(annotationRepository.findByEntityTypeAndEntityIdAndCampaignId(
              "actor", ENTITY_ID, CAMPAIGN_ID))
          .thenReturn(List.of(a));

      List<AnnotationView> result = sut.list(CAMPAIGN_ID, "actor", ENTITY_ID, AUTHOR_ID);

      assertThat(result).hasSize(1);
      assertThat(result.get(0).content()).isEqualTo("Note");
    }

    @Test
    @DisplayName("should throw UnauthorizedException when caller is not a member")
    void list_nonMember_throwsUnauthorized() {
      when(membershipPort.resolveRole(CAMPAIGN_ID, AUTHOR_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> sut.list(CAMPAIGN_ID, "actor", ENTITY_ID, AUTHOR_ID))
          .isInstanceOf(UnauthorizedException.class);
    }
  }

  // -------------------------------------------------------------------------
  // Delete
  // -------------------------------------------------------------------------

  @Nested
  @DisplayName("delete")
  class Delete {

    @Test
    @DisplayName("should delete when caller is the annotation author")
    void delete_byAuthor_succeeds() {
      when(membershipPort.resolveRole(CAMPAIGN_ID, AUTHOR_ID))
          .thenReturn(Optional.of(CampaignRole.PLAYER));
      Annotation annotation = stubAnnotation(AUTHOR_ID, "Note");
      when(annotationRepository.findById(ANNOTATION_ID)).thenReturn(Optional.of(annotation));

      sut.delete(CAMPAIGN_ID, ANNOTATION_ID, AUTHOR_ID);

      verify(annotationRepository).deleteById(ANNOTATION_ID);
    }

    @Test
    @DisplayName("should delete when caller is the campaign GM even if not the author")
    void delete_byGm_succeeds() {
      when(membershipPort.resolveRole(CAMPAIGN_ID, GM_ID)).thenReturn(Optional.of(CampaignRole.GM));
      Annotation annotation = stubAnnotation(AUTHOR_ID, "Note");
      when(annotationRepository.findById(ANNOTATION_ID)).thenReturn(Optional.of(annotation));

      sut.delete(CAMPAIGN_ID, ANNOTATION_ID, GM_ID);

      verify(annotationRepository).deleteById(ANNOTATION_ID);
    }

    @Test
    @DisplayName("should throw UnauthorizedException when caller is neither author nor GM")
    void delete_nonAuthorNonGm_throwsUnauthorized() {
      UUID other = UUID.randomUUID();
      when(membershipPort.resolveRole(CAMPAIGN_ID, other))
          .thenReturn(Optional.of(CampaignRole.PLAYER));
      Annotation annotation = stubAnnotation(AUTHOR_ID, "Note");
      when(annotationRepository.findById(ANNOTATION_ID)).thenReturn(Optional.of(annotation));

      assertThatThrownBy(() -> sut.delete(CAMPAIGN_ID, ANNOTATION_ID, other))
          .isInstanceOf(UnauthorizedException.class);
      verify(annotationRepository, never()).deleteById(any(UUID.class));
    }

    @Test
    @DisplayName("should throw AnnotationNotFoundException when annotation does not exist")
    void delete_missingAnnotation_throwsNotFound() {
      when(membershipPort.resolveRole(CAMPAIGN_ID, AUTHOR_ID))
          .thenReturn(Optional.of(CampaignRole.PLAYER));
      when(annotationRepository.findById(ANNOTATION_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> sut.delete(CAMPAIGN_ID, ANNOTATION_ID, AUTHOR_ID))
          .isInstanceOf(AnnotationNotFoundException.class);
    }

    @Test
    @DisplayName("should throw UnauthorizedException when caller is not a member")
    void delete_nonMember_throwsUnauthorized() {
      when(membershipPort.resolveRole(CAMPAIGN_ID, AUTHOR_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> sut.delete(CAMPAIGN_ID, ANNOTATION_ID, AUTHOR_ID))
          .isInstanceOf(UnauthorizedException.class);
      verify(annotationRepository, never()).findById(any(UUID.class));
    }
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private Annotation stubAnnotation(UUID authorId, String content) {
    return Annotation.create(
        ANNOTATION_ID,
        CAMPAIGN_ID,
        ENTITY_ID,
        AnnotationEntityType.actor,
        authorId,
        content,
        Instant.now());
  }
}
