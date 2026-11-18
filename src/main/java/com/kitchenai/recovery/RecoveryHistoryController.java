package com.kitchenai.recovery;

import com.kitchenai.common.ApiException;
import com.kitchenai.cooking.CookingSession;
import com.kitchenai.cooking.CookingSessionRepository;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/recovery-history")
public class RecoveryHistoryController {
  private final CookingSessionRepository sessions;
  private final RecipeRecoveryAttemptRepository attempts;
  private final RecoveryAiExecutionRepository executions;
  private final Path storage;

  public RecoveryHistoryController(CookingSessionRepository sessions,
      RecipeRecoveryAttemptRepository attempts, RecoveryAiExecutionRepository executions,
      @Value("${app.storage-path}") String path) {
    this.sessions=sessions; this.attempts=attempts; this.executions=executions;
    this.storage=Path.of(path).toAbsolutePath().normalize();
  }

  @GetMapping
  @Transactional
  public List<Map<String,Object>> list(@AuthenticationPrincipal UUID user) {
    List<UUID> sessionIds=sessions.findByUserIdOrderByStartedAtDesc(user).stream()
        .map(CookingSession::getId).toList();
    if(sessionIds.isEmpty()) return List.of();
    return attempts.findBySessionIdInOrderByCreatedAtDesc(sessionIds).stream().map(this::summary).toList();
  }

  @GetMapping("/{attemptId}")
  @Transactional
  public Map<String,Object> detail(@PathVariable UUID attemptId,@AuthenticationPrincipal UUID user) {
    RecipeRecoveryAttempt attempt=ownedAttempt(attemptId,user);
    Map<String,Object> result=summary(attempt);
    result.put("executions",executions.findByAttemptIdOrderByCreatedAtAsc(attemptId).stream()
        .map(e -> Map.of("operation",e.getOperation(),"provider",e.getProvider(),
            "model",e.getModel()==null?"":e.getModel(),"status",e.getStatus(),
            "response",e.getResponseJson()==null?"":e.getResponseJson(),
            "errorCode",e.getErrorCode()==null?"":e.getErrorCode(),
            "errorMessage",e.getErrorMessage()==null?"":e.getErrorMessage(),
            "durationMs",e.getDurationMs()==null?0:e.getDurationMs(),
            "createdAt",e.getCreatedAt())).toList());
    result.put("imageUrl",attempt.getImageObjectKey()==null?null:
        "/api/v1/recovery-history/"+attemptId+"/image");
    return result;
  }

  @PostMapping("/{attemptId}/reuse")
  @Transactional
  public Map<String,Object> reuse(@PathVariable UUID attemptId,@AuthenticationPrincipal UUID user) {
    RecipeRecoveryAttempt attempt=ownedAttempt(attemptId,user);
    Map<String,Object> result=summary(attempt);
    result.put("reuseMode","REFERENCE_ONLY");
    result.put("notice","Bu önceki tavsiyedir; mevcut malzeme ve pişirme koşullarıyla uyumunu kontrol etmeden uygulama.");
    return result;
  }

  @GetMapping("/{attemptId}/image")
  public ResponseEntity<ByteArrayResource> image(@PathVariable UUID attemptId,
      @AuthenticationPrincipal UUID user) throws IOException {
    RecipeRecoveryAttempt attempt=ownedAttempt(attemptId,user);
    if(attempt.getImageObjectKey()==null) throw new ApiException(HttpStatus.NOT_FOUND,"No image for this recovery attempt");
    Path file=storage.resolve(attempt.getImageObjectKey()).normalize();
    if(!file.startsWith(storage)||!Files.isRegularFile(file))
      throw new ApiException(HttpStatus.NOT_FOUND,"Recovery image not found");
    String name=file.getFileName().toString().toLowerCase(Locale.ROOT);
    MediaType type=name.endsWith(".png")?MediaType.IMAGE_PNG:MediaType.IMAGE_JPEG;
    return ResponseEntity.ok().contentType(type).cacheControl(CacheControl.noStore())
        .body(new ByteArrayResource(Files.readAllBytes(file)));
  }

  private RecipeRecoveryAttempt ownedAttempt(UUID id,UUID user) {
    RecipeRecoveryAttempt attempt=attempts.findById(id)
        .orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Recovery attempt not found"));
    boolean owned=sessions.findById(attempt.getSessionId())
        .map(CookingSession::getUserId).filter(user::equals).isPresent();
    if(!owned) throw new ApiException(HttpStatus.NOT_FOUND,"Recovery attempt not found");
    return attempt;
  }

  private Map<String,Object> summary(RecipeRecoveryAttempt a) {
    Map<String,Object> item=new LinkedHashMap<>();
    item.put("id",a.getId()); item.put("sessionId",a.getSessionId());
    item.put("recipeId",a.getRecipeId()); item.put("componentKey",a.getComponentKey());
    item.put("attemptNumber",a.getAttemptNumber()); item.put("status",a.getStatus());
    item.put("problemCategory",a.getUserProblemCategory());
    item.put("problemDescription",a.getUserProblemDescription());
    item.put("statusText",a.getUserStatusText()); item.put("desiredOutcome",a.getDesiredOutcome());
    item.put("recipeName",a.getFallbackRecipeName());
    item.put("observedState",a.getObservedStateJson()); item.put("targetState",a.getTargetStateJson());
    item.put("assessment",a.getAssessmentJson()); item.put("action",a.getActionJson());
    item.put("imageTrustScore",a.getImageTrustScore());
    item.put("targetProximityScore",a.getTargetProximityScore());
    item.put("analysisConfidence",a.getAnalysisConfidence()); item.put("createdAt",a.getCreatedAt());
    item.put("hasImage",a.getImageObjectKey()!=null);
    return item;
  }
}
