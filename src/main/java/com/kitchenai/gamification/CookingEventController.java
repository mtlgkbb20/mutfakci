package com.kitchenai.gamification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/events")
public class CookingEventController {

  private final CookingEventService eventService;
  private final JdbcTemplate jdbc;

  public CookingEventController(CookingEventService eventService, JdbcTemplate jdbc) {
    this.eventService = eventService;
    this.jdbc = jdbc;
  }

  @GetMapping
  public List<Map<String, Object>> list() {
    return eventService.list();
  }

  @GetMapping("/{id}")
  public Map<String, Object> detail(
      @PathVariable UUID id, @AuthenticationPrincipal UUID userId) {
    return eventService.detail(id, userId);
  }

  @PostMapping("/{id}/participation")
  public ResponseEntity<Void> join(
      @PathVariable UUID id, @AuthenticationPrincipal UUID userId) {
    eventService.join(id, userId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping(
      value = "/{id}/submissions",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Map<String, Object> submit(
      @PathVariable UUID id,
      @AuthenticationPrincipal UUID userId,
      @RequestParam(required = false) String caption,
      @RequestPart("images") List<MultipartFile> files)
      throws Exception {

    if (files == null || files.isEmpty() || files.size() > 8) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Upload 1-8 images");
    }

    List<byte[]> imageBytes = new ArrayList<>();
    List<String> contentTypes = new ArrayList<>();

    for (MultipartFile file : files) {
      String contentType = file.getContentType();

      if (!List.of("image/jpeg", "image/png", "image/webp").contains(contentType)
          || file.getSize() > 8 * 1024 * 1024) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Only JPEG, PNG or WebP up to 8 MB are accepted");
      }

      byte[] bytes = file.getBytes();
      if (!validImage(bytes, contentType)) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Invalid image content");
      }

      imageBytes.add(bytes);
      contentTypes.add(contentType);
    }

    UUID submissionId =
        eventService.submit(id, userId, caption, contentTypes, imageBytes);

    return Map.of("submissionId", submissionId);
  }

  @PutMapping("/{eventId}/submissions/{submissionId}/vote")
  public ResponseEntity<Void> vote(
      @PathVariable UUID eventId,
      @PathVariable UUID submissionId,
      @AuthenticationPrincipal UUID userId,
      @RequestBody Map<String, Integer> body) {

    eventService.vote(
        eventId, userId, submissionId, body.getOrDefault("score", 0));

    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/submissions")
  public List<Map<String, Object>> submissions(@PathVariable UUID id) {
    return eventService.submissions(id);
  }

  @GetMapping("/admin/{eventId}/images/{imageId}")
  public ResponseEntity<byte[]> adminImage(
      @AuthenticationPrincipal UUID adminId,
      @PathVariable UUID eventId,
      @PathVariable UUID imageId) {

    requireAdmin(adminId);
    Map<String, Object> image = eventService.adminImage(eventId, imageId);

    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType((String) image.get("content_type")))
        .body((byte[]) image.get("image_data"));
  }

  @GetMapping("/{eventId}/images/{imageId}")
  public ResponseEntity<byte[]> image(
      @PathVariable UUID eventId, @PathVariable UUID imageId) {

    Map<String, Object> image = eventService.image(eventId, imageId);

    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType((String) image.get("content_type")))
        .body((byte[]) image.get("image_data"));
  }

  @GetMapping("/admin/{id}/submissions")
  public List<Map<String, Object>> adminSubmissions(
      @AuthenticationPrincipal UUID adminId, @PathVariable UUID id) {
    requireAdmin(adminId);
    return eventService.adminSubmissions(id);
  }

  @PostMapping("/admin/{eventId}/submissions/{submissionId}/moderation")
  public ResponseEntity<Void> moderate(
      @AuthenticationPrincipal UUID adminId,
      @PathVariable UUID eventId,
      @PathVariable UUID submissionId,
      @RequestBody Map<String, String> body) {

    requireAdmin(adminId);
    eventService.moderate(
        eventId,
        submissionId,
        body.getOrDefault("status", ""),
        body.getOrDefault("reason", ""));

    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/results")
  public List<Map<String, Object>> results(@PathVariable UUID id) {
    return eventService.results(id);
  }

  @PostMapping("/admin")
  public Map<String, Object> create(
      @AuthenticationPrincipal UUID adminId, @RequestBody Map<String, Object> body) {
    requireAdmin(adminId);
    return Map.of("id", eventService.create(adminId, body));
  }

  @PostMapping("/admin/{id}/open-voting")
  public ResponseEntity<Void> openVoting(
      @AuthenticationPrincipal UUID adminId, @PathVariable UUID id) {
    requireAdmin(adminId);
    eventService.openVoting(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/admin/{id}/finalize")
  public Map<String, Object> finalizeEvent(
      @AuthenticationPrincipal UUID adminId, @PathVariable UUID id) {
    requireAdmin(adminId);
    return eventService.finalizeEvent(id);
  }

  @PostMapping("/admin/{id}/cancel")
  public ResponseEntity<Void> cancel(
      @AuthenticationPrincipal UUID adminId, @PathVariable UUID id) {
    requireAdmin(adminId);
    jdbc.update(
        "UPDATE cooking_event SET status = 'CANCELLED', updated_at = now() WHERE id = ?",
        id);
    return ResponseEntity.noContent().build();
  }

  private void requireAdmin(UUID userId) {
    Boolean isAdmin =
        jdbc.queryForObject(
            "SELECT is_admin FROM app_user WHERE id = ?", Boolean.class, userId);

    if (!Boolean.TRUE.equals(isAdmin)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
  }

  private boolean validImage(byte[] bytes, String contentType) {
    if (bytes.length < 12) {
      return false;
    }

    if ("image/jpeg".equals(contentType)) {
      return (bytes[0] & 0xFF) == 0xFF
          && (bytes[1] & 0xFF) == 0xD8
          && (bytes[2] & 0xFF) == 0xFF;
    }

    if ("image/png".equals(contentType)) {
      return (bytes[0] & 0xFF) == 0x89
          && bytes[1] == 0x50
          && bytes[2] == 0x4E
          && bytes[3] == 0x47;
    }

    return bytes[0] == 0x52
        && bytes[1] == 0x49
        && bytes[2] == 0x46
        && bytes[3] == 0x46
        && bytes[8] == 0x57
        && bytes[9] == 0x45
        && bytes[10] == 0x42
        && bytes[11] == 0x50;
  }
}
