package com.bluesteel.adapters.in.web.campaign;

import com.bluesteel.adapters.in.web.ApiResponse;
import com.bluesteel.application.model.campaign.CreateCampaignCommand;
import com.bluesteel.application.port.in.campaign.CreateCampaignUseCase;
import com.bluesteel.application.port.in.campaign.GetCampaignUseCase;
import com.bluesteel.application.port.in.campaign.ListCampaignsUseCase;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Campaign CRUD endpoints — create (admin-only), list, and get (D-024). */
@RestController
@RequestMapping("/api/v1/campaigns")
public class CampaignController {

  private final CreateCampaignUseCase createCampaignUseCase;
  private final GetCampaignUseCase getCampaignUseCase;
  private final ListCampaignsUseCase listCampaignsUseCase;

  public CampaignController(
      CreateCampaignUseCase createCampaignUseCase,
      GetCampaignUseCase getCampaignUseCase,
      ListCampaignsUseCase listCampaignsUseCase) {
    this.createCampaignUseCase = createCampaignUseCase;
    this.getCampaignUseCase = getCampaignUseCase;
    this.listCampaignsUseCase = listCampaignsUseCase;
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<CampaignResponse>> create(
      @Valid @RequestBody CreateCampaignRequest request) {
    UUID callerId = resolveUserId();
    CampaignResponse response =
        CampaignResponse.from(
            createCampaignUseCase.create(
                new CreateCampaignCommand(callerId, true, request.name(), request.gmUserId())));
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<CampaignResponse>>> list() {
    UUID callerId = resolveUserId();
    boolean isAdmin = isAdmin();
    List<CampaignResponse> campaigns =
        listCampaignsUseCase.list(callerId, isAdmin).stream().map(CampaignResponse::from).toList();
    return ResponseEntity.ok(ApiResponse.success(campaigns));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<CampaignResponse>> get(@PathVariable UUID id) {
    UUID callerId = resolveUserId();
    boolean isAdmin = isAdmin();
    CampaignResponse response =
        CampaignResponse.from(getCampaignUseCase.get(id, callerId, isAdmin));
    return ResponseEntity.ok(ApiResponse.success(response));
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
