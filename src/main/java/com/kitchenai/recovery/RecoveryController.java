package com.kitchenai.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kitchenai.common.ApiException;
import com.kitchenai.cooking.CookingSession;
import com.kitchenai.cooking.CookingSessionRepository;
import com.kitchenai.recipe.Recipe;
import com.kitchenai.recipe.RecipeRepository;
import com.kitchenai.recovery.RecoveryContracts.*;
import jakarta.transaction.Transactional;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/cooking-sessions/{sessionId}/recovery-attempts")
public class RecoveryController {
  private static final long MAX_BYTES = 10_000_000L;
  private static final long MAX_PIXELS = 20_000_000L;
  private final CookingSessionRepository sessions;
  private final RecipeRepository recipes;
  private final RecipeRecoveryAttemptRepository attempts;
  private final RecoveryVisionService vision;
  private final ObjectMapper json;
  private final Path storage;

  public RecoveryController(CookingSessionRepository sessions, RecipeRepository recipes,
      RecipeRecoveryAttemptRepository attempts, RecoveryVisionService vision, ObjectMapper json,
      @Value("${app.storage-path}") String path) {
    this.sessions=sessions; this.recipes=recipes; this.attempts=attempts;
    this.vision=vision; this.json=json; this.storage=Path.of(path);
  }

  @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  @Transactional
  public Response initial(@PathVariable UUID sessionId, @AuthenticationPrincipal UUID user,
      @RequestPart("image") MultipartFile image,
      @RequestPart(value="problemCategory", required=false) String category,
      @RequestPart(value="problemDescription", required=false) String description,
      @RequestPart(value="desiredOutcome", required=false) String outcome,
      @RequestPart(value="componentKey", required=false) String componentKey) {
    CookingSession session=ownSession(sessionId,user);
    return createAttempt(session,null,image,category,description,null,outcome,componentKey);
  }

  @PostMapping(value="/{attemptId}/confirm-action", consumes=MediaType.APPLICATION_JSON_VALUE)
  @Transactional
  public Map<String,Object> confirm(@PathVariable UUID sessionId,@PathVariable UUID attemptId,
      @AuthenticationPrincipal UUID user,@RequestBody ConfirmRequest request) {
    ownSession(sessionId,user);
    RecipeRecoveryAttempt attempt=ownAttempt(sessionId,attemptId);
    if(!request.confirmed()) throw new ApiException(HttpStatus.CONFLICT,"Correction must be confirmed before follow-up");
    try { attempt.confirmAction(); }
    catch(IllegalStateException e){ throw new ApiException(HttpStatus.CONFLICT,"No pending correction for this attempt"); }
    attempts.save(attempt);
    return Map.of("status","AWAITING_FOLLOW_UP","message","Yeni bir fotoğraf ve kısa durum açıklaması gönder.");
  }

  @PostMapping(value="/{attemptId}/follow-up", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  @Transactional
  public Response followUp(@PathVariable UUID sessionId,@PathVariable UUID attemptId,
      @AuthenticationPrincipal UUID user,@RequestPart("image") MultipartFile image,
      @RequestPart("statusText") String statusText) {
    CookingSession session=ownSession(sessionId,user);
    RecipeRecoveryAttempt parent=ownAttempt(sessionId,attemptId);
    if(!"AWAITING_FOLLOW_UP".equals(parent.getStatus()) || parent.getUserActionConfirmedAt()==null)
      throw new ApiException(HttpStatus.CONFLICT,"Confirm the previous correction before sending a follow-up");
    if(statusText==null || statusText.isBlank() || statusText.length()>1000)
      throw new ApiException(HttpStatus.BAD_REQUEST,"A short status description is required");
    return createAttempt(session,parent,image,null,null,statusText,parentDesiredOutcome(parent),parent.getComponentKey());
  }

  @PostMapping(value="/{attemptId}/decision", consumes=MediaType.APPLICATION_JSON_VALUE)
  @Transactional
  public Response decision(@PathVariable UUID sessionId,@PathVariable UUID attemptId,
      @AuthenticationPrincipal UUID user,@RequestBody DecisionRequest request) {
    ownSession(sessionId,user);
    RecipeRecoveryAttempt attempt=ownAttempt(sessionId,attemptId);
    if(!"RECOVERY_OFFERED".equals(attempt.getStatus()))
      throw new ApiException(HttpStatus.CONFLICT,"This attempt has no recovery/remake choice");
    if("REMAKE_COMPONENT".equals(request.choice())) {
      attempt.applyAnalysis(attempt.getImageTrustScore(),null,attempt.getTargetProximityScore(),
          "{}", "{}", "Kullanıcı bileşeni yeniden yapmayı seçti.", null, "REMAKE_RECOMMENDED");
      attempts.save(attempt);
      return response(Status.REMAKE_COMPONENT,attempt,null,null,null,null,null,
          "Bu bileşeni yeniden hazırlamak en iyi seçenek.");
    }
    if(!"TRY_RECOVERY".equals(request.choice()))
      throw new ApiException(HttpStatus.BAD_REQUEST,"Choice must be TRY_RECOVERY or REMAKE_COMPONENT");
    JsonNode action;
    try { action=json.readTree(attemptAction(attempt)); }
    catch(Exception e){ throw new ApiException(HttpStatus.CONFLICT,"No validated recovery action is available"); }
    attempt.applyAnalysis(attempt.getImageTrustScore(),null,attempt.getTargetProximityScore(),
        "{}", "{}", "Kullanıcı kurtarmayı seçti.", action.toString(), "CORRECTION_PROPOSED");
    attempts.save(attempt);
    return response(Status.CORRECTION_PROPOSED,attempt,null,null,null,
        json.convertValue(action,Action.class),
        new FollowUp(true,true,true,"Düzeltmeyi uygula; ardından yeni fotoğraf ve kısa durum açıklaması gönder."),
        "Küçük ve kontrollü bir düzeltme deneyebilirsin.");
  }

  @GetMapping
  public List<RecipeRecoveryAttempt> history(@PathVariable UUID sessionId,@AuthenticationPrincipal UUID user) {
    ownSession(sessionId,user);
    return attempts.findAll().stream().filter(a->sessionId.equals(a.getSessionId()))
        .sorted(Comparator.comparing(RecipeRecoveryAttempt::getAttemptNumber)).toList();
  }

  private Response createAttempt(CookingSession session,RecipeRecoveryAttempt parent,MultipartFile image,
      String category,String description,String statusText,String outcome,String componentKey) {
    if(image==null || image.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST,"A photo is required");
    String mime=image.getContentType();
    if(!List.of("image/jpeg","image/png").contains(mime))
      throw new ApiException(HttpStatus.BAD_REQUEST,"Use a JPEG or PNG photo");
    if(image.getSize()>MAX_BYTES) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,"Image is too large");
    try {
      byte[] bytes=image.getBytes();
      BufferedImage decoded=ImageIO.read(new ByteArrayInputStream(bytes));
      if(decoded==null) throw new ApiException(HttpStatus.BAD_REQUEST,"The image could not be decoded");
      long pixels=(long)decoded.getWidth()*decoded.getHeight();
      if(decoded.getWidth()<160 || decoded.getHeight()<160 || pixels>MAX_PIXELS)
        throw new ApiException(HttpStatus.BAD_REQUEST,"Image dimensions are not supported");
      String key=store(session.getUserId(),bytes,mime);
      Recipe recipe=session.getRecipeId()==null?null:recipes.findById(session.getRecipeId())
          .filter(r->r.getUserId()==null || r.getUserId().equals(session.getUserId()))
          .orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Recipe not found"));
      String component=componentKey==null||componentKey.isBlank()
          ? (parent==null?"stage-"+(session.getCurrentStage()+1):parent.getComponentKey()):componentKey.trim();
      if(component.length()>120) throw new ApiException(HttpStatus.BAD_REQUEST,"Invalid component key");
      int attemptNumber=attempts.findTopBySessionIdAndComponentKeyOrderByAttemptNumberDesc(session.getId(),component)
          .map(RecipeRecoveryAttempt::getAttemptNumber).orElse(0)+1;
      RecipeRecoveryAttempt attempt=attempts.save(new RecipeRecoveryAttempt(
          session.getRecipeId(),session.getId(),parent==null?null:parent.getId(),component,
          session.getCurrentStage()+1,attemptNumber,key,category,description,statusText,outcome));
      if(!vision.available()) {
        attempt.markUnavailable("No vision model is configured.");
        attempts.save(attempt);
        return response(Status.VISION_PROVIDER_UNAVAILABLE,attempt,null,null,null,null,null,
            "Görsel analiz servisi şu anda yapılandırılmamış. Fotoğrafın analiz edilmedi; tarifine devam edebilirsin.");
      }
      String context=buildContext(recipe,session,component,category,description,statusText,outcome,parent);
      Analysis analysis=vision.analyze(bytes,mime,context);
      if(analysis.imageTrustScore()<60) {
        attempt.markImageRejected(analysis.imageTrustScore(),json.writeValueAsString(Map.of(
            "problemSummary",analysis.problemSummary(),"observedState",analysis.observedState())));
        attempts.save(attempt);
        return response(Status.REQUEST_NEW_IMAGE,attempt,analysis,null,null,null,null,
            "Fotoğraf yeterince net veya ilgili değil. Bileşeni daha yakından, iyi ışıkta yeniden çek.");
      }
      if(analysis.analysisConfidence().doubleValue()<0.65 || analysis.action().type()==ActionType.NONE) {
        attempt.applyAnalysis(analysis.imageTrustScore(),analysis.analysisConfidence(),analysis.targetProximityScore(),
            json.writeValueAsString(Map.of("observedState",analysis.observedState())),
            json.writeValueAsString(Map.of("targetState",analysis.targetState())),
            json.writeValueAsString(Map.of("reason",analysis.reason())),null,"ANALYSIS_REJECTED");
        attempts.save(attempt);
        return response(Status.NEEDS_CLARIFICATION,attempt,analysis,null,null,null,null,
            "Güvenli bir düzeltme önermek için biraz daha bilgiye ihtiyacım var: "+analysis.reason());
      }
      int score=analysis.targetProximityScore();
      Decision decision=score>=90?Decision.READY_TO_CONTINUE:score>=75?Decision.MINOR_CORRECTION:
          score>=50?Decision.RECOVERABLE_BUT_MAJOR_GAP:Decision.REMAKE_COMPONENT;
      Status status=score>=90?Status.READY_TO_CONTINUE:score>=75?Status.CORRECTION_PROPOSED:
          score>=50?Status.OFFER_RECOVERY_OR_REMAKE:Status.REMAKE_COMPONENT;
      Action action=score>=90||score<50?null:analysis.action();
      String lifecycle=score>=90?"READY_TO_CONTINUE":score>=75?"CORRECTION_PROPOSED":
          score>=50?"RECOVERY_OFFERED":"REMAKE_RECOMMENDED";
      attempt.applyAnalysis(analysis.imageTrustScore(),analysis.analysisConfidence(),score,
          json.writeValueAsString(Map.of("observedState",analysis.observedState())),
          json.writeValueAsString(Map.of("targetState",analysis.targetState())),
          json.writeValueAsString(Map.of("gapSummary",analysis.gapSummary(),"reason",analysis.reason(),"decision",decision.name())),
          action==null?null:json.writeValueAsString(action),lifecycle);
      attempts.save(attempt);
      FollowUp follow=score>=75&&score<90?new FollowUp(true,true,true,
          "Düzeltmeyi uygula; ardından yeni fotoğraf ve kısa durum açıklaması gönder."):null;
      String message=score>=90?"Hedef kıvama yeterince yakın. Tarifin sonraki adımına geçebilirsin.":
          score>=75?"Küçük bir düzeltme gerekiyor.":score>=50?
          "Bu bileşen kurtarılabilir; ancak yeniden yapmak daha iyi sonuç verebilir.": 
          "Bu bileşen mevcut haliyle makul şekilde kurtarılamıyor; yalnızca bu bileşeni yeniden hazırlamanı öneririm.";
      return response(status,attempt,analysis,decision,action,follow,message);
    } catch(ApiException e) { throw e; }
    catch(IOException e) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,"Photo could not be stored"); }
  }

  private String buildContext(Recipe recipe,CookingSession session,String component,String category,
      String description,String statusText,String outcome,RecipeRecoveryAttempt parent) {
    try {
      Map<String,Object> c=new LinkedHashMap<>();
      c.put("componentKey",component); c.put("stageOrder",session.getCurrentStage()+1);
      c.put("problemCategory",category); c.put("problemDescription",description);
      c.put("followUpStatus",statusText); c.put("desiredOutcome",outcome);
      if(recipe!=null) { c.put("recipeTitle",recipe.getTitle()); c.put("productType",recipe.getProductType());
        c.put("ingredients",json.readTree(recipe.getIngredientsJson())); c.put("stages",json.readTree(recipe.getStagesJson())); }
      if(parent!=null) { c.put("previousAttempt",parent.getId()); c.put("previousStatus",parent.getStatus()); }
      return json.writeValueAsString(c);
    } catch(Exception e) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,"Recipe context could not be prepared"); }
  }

  private String store(UUID user,byte[] bytes,String mime) throws IOException {
    Path dir=storage.resolve(user.toString()).normalize();
    Files.createDirectories(dir);
    String key=user+"/"+UUID.randomUUID()+(mime.equals("image/png")?".png":".jpg");
    Path target=storage.resolve(key).normalize();
    if(!target.startsWith(storage.normalize())) throw new IOException("Invalid storage path");
    Files.write(target,bytes,StandardOpenOption.CREATE_NEW);
    return key;
  }
  private CookingSession ownSession(UUID id,UUID user) {
    return sessions.findById(id).filter(s->s.getUserId().equals(user))
        .orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Session not found"));
  }
  private RecipeRecoveryAttempt ownAttempt(UUID session,UUID id) {
    return attempts.findById(id).filter(a->session.equals(a.getSessionId()))
        .orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Recovery attempt not found"));
  }
  private String attemptAction(RecipeRecoveryAttempt attempt) {
    try { var field=RecipeRecoveryAttempt.class.getDeclaredField("actionJson"); field.setAccessible(true);
      return (String)field.get(attempt); } catch(Exception e){ return null; }
  }
  private String parentDesiredOutcome(RecipeRecoveryAttempt parent) { return null; }
  private Response response(Status status,RecipeRecoveryAttempt attempt,Analysis a,Decision d,
      Action action,FollowUp follow,String message) {
    Problem problem=a==null?null:new Problem(a.problemCategory(),a.problemSummary());
    Assessment assessment=a==null?null:new Assessment(a.targetState(),a.gapSummary(),a.reason());
    return new Response(status,attempt.getImageTrustScore(),a==null?null:a.analysisConfidence(),
        attempt.getTargetProximityScore(),problem,assessment,d,action,follow,attempt.getId(),message);
  }
}
