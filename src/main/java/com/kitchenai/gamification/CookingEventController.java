package com.kitchenai.gamification;

import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/events")
public class CookingEventController {
  private final CookingEventService service;
  private final JdbcTemplate jdbc;
  public CookingEventController(CookingEventService service, JdbcTemplate jdbc) { this.service=service; this.jdbc=jdbc; }

  @GetMapping public List<Map<String,Object>> list() { return service.list(); }
  @GetMapping("/{id}") public Map<String,Object> detail(@PathVariable UUID id,@AuthenticationPrincipal UUID user) { return service.detail(id,user); }
  @PostMapping("/{id}/participation") public ResponseEntity<Void> join(@PathVariable UUID id,@AuthenticationPrincipal UUID user) {
    service.join(id,user); return ResponseEntity.noContent().build();
  }

  @PostMapping(value="/{id}/submissions",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  public Map<String,Object> submit(@PathVariable UUID id,@AuthenticationPrincipal UUID user,
      @RequestParam(required=false) String caption,@RequestParam("images") List<MultipartFile> files) throws Exception {
    if(files==null || files.isEmpty() || files.size()>8) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"Upload 1-8 images");
    List<byte[]> bytes=new ArrayList<>(); List<String> types=new ArrayList<>();
    for(MultipartFile file:files) {
      String type=file.getContentType();
      if(!List.of("image/jpeg","image/png","image/webp").contains(type) || file.getSize()>8*1024*1024)
        throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"Only JPEG, PNG or WebP up to 8 MB are accepted");
      byte[] data=file.getBytes();
      if(!validImage(data,type)) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid image content");
      bytes.add(data); types.add(type);
    }
    return Map.of("submissionId",service.submit(id,user,caption,types,bytes));
  }

  @PutMapping("/{eventId}/submissions/{submissionId}/vote")
  public ResponseEntity<Void> vote(@PathVariable UUID eventId,@PathVariable UUID submissionId,
      @AuthenticationPrincipal UUID user,@RequestBody Map<String,Integer> body) {
    service.vote(eventId,user,submissionId,body.getOrDefault("score",0)); return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/submissions") public List<Map<String,Object>> submissions(@PathVariable UUID id) { return service.submissions(id); }
  @GetMapping("/admin/{eventId}/images/{imageId}") public ResponseEntity<byte[]> adminImage(@AuthenticationPrincipal UUID admin,@PathVariable UUID eventId,@PathVariable UUID imageId) {
    requireAdmin(admin); Map<String,Object> row=service.adminImage(eventId,imageId);
    return ResponseEntity.ok().contentType(MediaType.parseMediaType((String)row.get("content_type"))).body((byte[])row.get("image_data"));
  }
  @GetMapping("/{eventId}/images/{imageId}") public ResponseEntity<byte[]> image(@PathVariable UUID eventId,@PathVariable UUID imageId) {
    Map<String,Object> row=service.image(eventId,imageId);
    return ResponseEntity.ok().contentType(MediaType.parseMediaType((String)row.get("content_type"))).body((byte[])row.get("image_data"));
  }
  @GetMapping("/admin/{id}/submissions") public List<Map<String,Object>> adminSubmissions(@AuthenticationPrincipal UUID admin,@PathVariable UUID id) {
    requireAdmin(admin); return service.adminSubmissions(id);
  }
  @PostMapping("/admin/{eventId}/submissions/{submissionId}/moderation") public ResponseEntity<Void> moderate(
      @AuthenticationPrincipal UUID admin,@PathVariable UUID eventId,@PathVariable UUID submissionId,@RequestBody Map<String,String> body) {
    requireAdmin(admin); service.moderate(eventId,submissionId,body.getOrDefault("status",""),body.getOrDefault("reason","")); return ResponseEntity.noContent().build();
  }
  @GetMapping("/{id}/results") public List<Map<String,Object>> results(@PathVariable UUID id) { return service.results(id); }

  @PostMapping("/admin") public Map<String,Object> create(@AuthenticationPrincipal UUID admin,@RequestBody Map<String,Object> body) {
    requireAdmin(admin); return Map.of("id",service.create(admin,body));
  }
  @PostMapping("/admin/{id}/open-voting") public ResponseEntity<Void> openVoting(@AuthenticationPrincipal UUID admin,@PathVariable UUID id) {
    requireAdmin(admin); service.openVoting(id); return ResponseEntity.noContent().build();
  }
  @PostMapping("/admin/{id}/finalize") public Map<String,Object> finalizeEvent(@AuthenticationPrincipal UUID admin,@PathVariable UUID id) {
    requireAdmin(admin); return service.finalizeEvent(id);
  }
  @PostMapping("/admin/{id}/cancel") public ResponseEntity<Void> cancel(@AuthenticationPrincipal UUID admin,@PathVariable UUID id) {
    requireAdmin(admin); jdbc.update("UPDATE cooking_event SET status='CANCELLED',updated_at=now() WHERE id=?",id); return ResponseEntity.noContent().build();
  }

  private void requireAdmin(UUID user) {
    Boolean admin=jdbc.queryForObject("SELECT is_admin FROM app_user WHERE id=?",Boolean.class,user);
    if(!Boolean.TRUE.equals(admin)) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN);
  }
  private boolean validImage(byte[] b,String type) {
    if(b.length<12) return false;
    if("image/jpeg".equals(type)) return (b[0]&255)==0xFF && (b[1]&255)==0xD8 && (b[2]&255)==0xFF;
    if("image/png".equals(type)) return (b[0]&255)==0x89 && b[1]==0x50 && b[2]==0x4E && b[3]==0x47;
    return b[0]==0x52 && b[1]==0x49 && b[2]==0x46 && b[3]==0x46 && b[8]==0x57 && b[9]==0x45 && b[10]==0x42 && b[11]==0x50;
  }
}
