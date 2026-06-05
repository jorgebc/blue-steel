package com.bluesteel.application.service.annotation;

import com.bluesteel.application.model.annotation.AnnotationView;
import com.bluesteel.application.model.annotation.CreateAnnotationCommand;
import com.bluesteel.application.port.in.annotation.CreateAnnotationUseCase;
import com.bluesteel.application.port.in.annotation.DeleteAnnotationUseCase;
import com.bluesteel.application.port.in.annotation.ListAnnotationsUseCase;
import com.bluesteel.application.port.out.annotation.AnnotationRepository;
import com.bluesteel.application.port.out.campaign.CampaignMembershipPort;
import com.bluesteel.domain.annotation.Annotation;
import com.bluesteel.domain.annotation.AnnotationEntityType;
import com.bluesteel.domain.campaign.CampaignRole;
import com.bluesteel.domain.exception.AnnotationNotFoundException;
import com.bluesteel.domain.exception.UnauthorizedException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates annotation create, list, and delete. Authorization rules (D-011):
 *
 * <ul>
 *   <li>Create — any campaign member
 *   <li>List — any campaign member
 *   <li>Delete — the annotation's own author or the campaign GM
 * </ul>
 */
@Service
public class AnnotationService
    implements CreateAnnotationUseCase, ListAnnotationsUseCase, DeleteAnnotationUseCase {

  private static final Logger log = LoggerFactory.getLogger(AnnotationService.class);

  private final AnnotationRepository annotationRepository;
  private final CampaignMembershipPort membershipPort;

  public AnnotationService(
      AnnotationRepository annotationRepository, CampaignMembershipPort membershipPort) {
    this.annotationRepository = annotationRepository;
    this.membershipPort = membershipPort;
  }

  @Override
  public AnnotationView create(CreateAnnotationCommand command) {
    log.info(
        "Creating annotation campaignId={} entityType={} entityId={} authorId={}",
        command.campaignId(),
        command.entityType(),
        command.entityId(),
        command.authorId());
    requireMember(command.campaignId(), command.authorId());

    AnnotationEntityType entityType = AnnotationEntityType.valueOf(command.entityType());
    Annotation annotation =
        Annotation.create(
            UUID.randomUUID(),
            command.campaignId(),
            command.entityId(),
            entityType,
            command.authorId(),
            command.content(),
            Instant.now());

    Annotation saved = annotationRepository.save(annotation);
    return toView(saved);
  }

  @Override
  public List<AnnotationView> list(
      UUID campaignId, String entityType, UUID entityId, UUID callerId) {
    log.info(
        "Listing annotations campaignId={} entityType={} entityId={} callerId={}",
        campaignId,
        entityType,
        entityId,
        callerId);
    requireMember(campaignId, callerId);

    return annotationRepository
        .findByEntityTypeAndEntityIdAndCampaignId(entityType, entityId, campaignId)
        .stream()
        .map(this::toView)
        .toList();
  }

  @Override
  public void delete(UUID campaignId, UUID annotationId, UUID callerId) {
    log.info(
        "Deleting annotation annotationId={} campaignId={} callerId={}",
        annotationId,
        campaignId,
        callerId);
    requireMember(campaignId, callerId);

    Annotation annotation =
        annotationRepository
            .findById(annotationId)
            .orElseThrow(
                () -> new AnnotationNotFoundException("Annotation not found: " + annotationId));

    boolean isAuthor = annotation.authorId().equals(callerId);
    boolean isGm =
        membershipPort
            .resolveRole(campaignId, callerId)
            .map(role -> role == CampaignRole.GM)
            .orElse(false);

    if (!isAuthor && !isGm) {
      throw new UnauthorizedException(
          "Only the annotation's author or a campaign GM may delete it");
    }

    annotationRepository.deleteById(annotationId);
  }

  private void requireMember(UUID campaignId, UUID callerId) {
    membershipPort
        .resolveRole(campaignId, callerId)
        .orElseThrow(() -> new UnauthorizedException("Caller is not a member of this campaign"));
  }

  private AnnotationView toView(Annotation a) {
    return new AnnotationView(
        a.id(),
        a.campaignId(),
        a.entityId(),
        a.entityType().value(),
        a.authorId(),
        a.content(),
        a.createdAt());
  }
}
