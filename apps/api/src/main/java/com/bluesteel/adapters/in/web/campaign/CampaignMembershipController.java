package com.bluesteel.adapters.in.web.campaign;

import com.bluesteel.adapters.in.web.ApiResponse;
import com.bluesteel.application.model.campaign.ChangeMemberRoleCommand;
import com.bluesteel.application.model.campaign.InviteCampaignMemberCommand;
import com.bluesteel.application.model.campaign.RemoveMemberCommand;
import com.bluesteel.application.port.in.campaign.ChangeMemberRoleUseCase;
import com.bluesteel.application.port.in.campaign.InviteCampaignMemberUseCase;
import com.bluesteel.application.port.in.campaign.ListCampaignMembersUseCase;
import com.bluesteel.application.port.in.campaign.RemoveMemberUseCase;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Campaign-scoped membership management — invite, change role, remove. GM authorization is enforced
 * in the use-case services (D-043), so no {@code @PreAuthorize} guards the controller (D-064).
 */
@RestController
@RequestMapping("/api/v1/campaigns")
public class CampaignMembershipController {

  private final InviteCampaignMemberUseCase inviteCampaignMemberUseCase;
  private final ListCampaignMembersUseCase listCampaignMembersUseCase;
  private final ChangeMemberRoleUseCase changeMemberRoleUseCase;
  private final RemoveMemberUseCase removeMemberUseCase;

  public CampaignMembershipController(
      InviteCampaignMemberUseCase inviteCampaignMemberUseCase,
      ListCampaignMembersUseCase listCampaignMembersUseCase,
      ChangeMemberRoleUseCase changeMemberRoleUseCase,
      RemoveMemberUseCase removeMemberUseCase) {
    this.inviteCampaignMemberUseCase = inviteCampaignMemberUseCase;
    this.listCampaignMembersUseCase = listCampaignMembersUseCase;
    this.changeMemberRoleUseCase = changeMemberRoleUseCase;
    this.removeMemberUseCase = removeMemberUseCase;
  }

  /** Lists the campaign's members; accessible to any campaign member or the platform admin. */
  @GetMapping("/{id}/members")
  public ResponseEntity<ApiResponse<List<CampaignMemberResponse>>> listMembers(
      @PathVariable UUID id) {
    List<CampaignMemberResponse> members =
        listCampaignMembersUseCase.list(id, resolveUserId(), isAdmin()).stream()
            .map(CampaignMemberResponse::from)
            .toList();
    return ResponseEntity.ok(ApiResponse.success(members));
  }

  /** Invites a member: 201 when a new account was created, 200 when an existing user was added. */
  @PostMapping("/{id}/invitations")
  public ResponseEntity<ApiResponse<InviteCampaignMemberResponse>> invite(
      @PathVariable UUID id, @Valid @RequestBody InviteCampaignMemberRequest request) {
    boolean created =
        inviteCampaignMemberUseCase.invite(
            new InviteCampaignMemberCommand(id, resolveUserId(), request.email(), request.role()));

    InviteCampaignMemberResponse body =
        new InviteCampaignMemberResponse(
            request.email(), request.role().name().toLowerCase(), created);

    HttpStatus status = created ? HttpStatus.CREATED : HttpStatus.OK;
    return ResponseEntity.status(status).body(ApiResponse.success(body));
  }

  @PatchMapping("/{id}/members/{uid}")
  public ResponseEntity<ApiResponse<Void>> changeRole(
      @PathVariable UUID id,
      @PathVariable UUID uid,
      @Valid @RequestBody ChangeMemberRoleRequest request) {
    changeMemberRoleUseCase.change(
        new ChangeMemberRoleCommand(id, resolveUserId(), uid, request.role()));
    return ResponseEntity.ok(ApiResponse.success(null));
  }

  @DeleteMapping("/{id}/members/{uid}")
  public ResponseEntity<ApiResponse<Void>> remove(@PathVariable UUID id, @PathVariable UUID uid) {
    removeMemberUseCase.remove(new RemoveMemberCommand(id, resolveUserId(), uid));
    return ResponseEntity.ok(ApiResponse.success(null));
  }

  private UUID resolveUserId() {
    return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
  }

  private boolean isAdmin() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch("ROLE_ADMIN"::equals);
  }
}
