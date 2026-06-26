package com.bluesteel.adapters.in.web.campaign;

import com.bluesteel.application.model.campaign.CampaignArchive;
import com.bluesteel.application.port.in.campaign.ExportCampaignUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Streams a campaign's complete dataset as a downloadable raw-JSON archive (D-112) for the GM or an
 * admin. The archive is written straight to the response output stream so no full copy is buffered
 * in heap; authorization/cap errors are thrown by the use case before streaming begins, so they
 * still flow through {@code GlobalExceptionHandler} as the standard error envelope.
 */
@RestController
@RequestMapping("/api/v1/campaigns")
public class CampaignExportController {

  /**
   * Dedicated archive serializer: the shared {@code ObjectMapper} bean is a bare instance used only
   * for reading JSONB to maps, so it lacks JavaTime support. This one registers the classpath
   * modules and emits ISO-8601 timestamps, matching the API's date contract for the exported file.
   */
  private static final ObjectMapper EXPORT_MAPPER =
      new ObjectMapper()
          .findAndRegisterModules()
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  private final ExportCampaignUseCase exportCampaignUseCase;

  public CampaignExportController(ExportCampaignUseCase exportCampaignUseCase) {
    this.exportCampaignUseCase = exportCampaignUseCase;
  }

  @GetMapping("/{id}/export")
  public ResponseEntity<StreamingResponseBody> export(@PathVariable UUID id) {
    // Resolve and authorize first so 403/404/422 surface as the standard envelope, before
    // streaming.
    CampaignArchive archive = exportCampaignUseCase.export(id, resolveUserId(), isAdmin());

    String filename = slug(archive.campaign().name()) + "-export.json";
    StreamingResponseBody body = outputStream -> EXPORT_MAPPER.writeValue(outputStream, archive);

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .contentType(MediaType.APPLICATION_JSON)
        .body(body);
  }

  private static String slug(String name) {
    String slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    return slug.isBlank() ? "campaign" : slug;
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
