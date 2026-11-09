package com.kitchenai.recovery;

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
import java.time.Instant;
import java.time.Duration;
import java.util.*;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/cooking-sessions/{sessionId}/recovery-attempts")
public class RecoveryController {
  private static final Logger log = LoggerFactory.getLogger(RecoveryController.class);
  private static final long MAX_BYTES=10_000_000L, MAX_PIXELS=20_000_000L;
  private static final long MAX_ATTEMPTS_PER_HOUR=20;
  private static final Set<String> PROBLEM_CATEGORIES=Set.of("TOO_HARD","TOO_SOFT","TOO_STICKY",
      "NOT_RISING","NOT_HOLDING_SHAPE","SPLITTING","CURDLED","OVERCOOKED","UNDERCOOKED","OTHER");
  private final CookingSessionRepository sessions;
  private final RecipeRepository recipes;
  private final RecipeRecoveryAttemptRepository attempts;
  private final RecoveryAiExecutionRepository executions;
  private final String visionModel;
  private final String textModel;
  private final RecoveryVisionService vision;
  private final TextFallbackAdviceService textFallback;
  private final ObjectMapper json;
  private final Path storage;

  public RecoveryController(CookingSessionRepository sessions,RecipeRepository recipes,
      RecipeRecoveryAttemptRepository attempts,RecoveryAiExecutionRepository executions,
      RecoveryVisionService vision,TextFallbackAdviceService textFallback,ObjectMapper json,
      @Value("${app.storage-path}") String path,
      @Value("${app.ai.runware.vision-model:unknown}") String visionModel,
      @Value("${app.ai.runware.text-fallback-model:unknown}") String textModel){
    this.sessions=sessions;this.recipes=recipes;this.attempts=attempts;this.executions=executions;
    this.visionModel=visionModel;this.textModel=textModel;
    this.vision=vision;this.textFallback=textFallback;this.json=json;this.storage=Path.of(path);
  }

  @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  @Transactional
  public Response initial(@PathVariable UUID sessionId,@AuthenticationPrincipal UUID user,
      @RequestPart("image") MultipartFile image,
      @RequestPart("recipeName") String recipeName,
      @RequestPart("problemDescription") String description,
      @RequestPart(value="problemCategory",required=false) String category,
      @RequestPart(value="desiredOutcome",required=false) String outcome,
      @RequestPart(value="componentKey",required=false) String componentKey){
    CookingSession session=ownSession(sessionId,user);
    if(category!=null&&!category.isBlank()&&!PROBLEM_CATEGORIES.contains(category))
      throw new ApiException(HttpStatus.BAD_REQUEST,"Unsupported problem category");
    return createAttempt(session,null,image,recipeName,category,description,null,outcome,componentKey);
  }


  @PostMapping(value="/{attemptId}/confirm-action",consumes=MediaType.APPLICATION_JSON_VALUE)
  @Transactional
  public Map<String,Object> confirm(@PathVariable UUID sessionId,@PathVariable UUID attemptId,
      @AuthenticationPrincipal UUID user,@RequestBody ConfirmRequest request){
    ownSession(sessionId,user);RecipeRecoveryAttempt attempt=ownAttempt(sessionId,attemptId);
    if(request==null||!request.confirmed())
      throw new ApiException(HttpStatus.CONFLICT,"Confirm that you applied the correction first");
    try{attempt.confirmAction();}
    catch(IllegalStateException e){throw new ApiException(HttpStatus.CONFLICT,"No pending correction for this attempt");}
    attempts.save(attempt);
    return Map.of("status","AWAITING_FOLLOW_UP","message","Yeni bir fotoğraf ve kısa durum açıklaması gönder.");
  }

  @PostMapping(value="/{attemptId}/follow-up",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  @Transactional
  public Response followUp(@PathVariable UUID sessionId,@PathVariable UUID attemptId,
      @AuthenticationPrincipal UUID user,@RequestPart("image") MultipartFile image,
      @RequestPart("statusText") String statusText){
    CookingSession session=ownSession(sessionId,user);RecipeRecoveryAttempt parent=ownAttempt(sessionId,attemptId);
    boolean pendingCorrection="AWAITING_FOLLOW_UP".equals(parent.getStatus())
        &&parent.getUserActionConfirmedAt()!=null;
    boolean retryingRejectedFollowUp=("IMAGE_REJECTED".equals(parent.getStatus())
        ||"ANALYSIS_REJECTED".equals(parent.getStatus()))&&parent.getParentAttemptId()!=null;
    if(!pendingCorrection&&!retryingRejectedFollowUp)
      throw new ApiException(HttpStatus.CONFLICT,"Confirm the previous correction before sending a follow-up");
    if(statusText==null||statusText.isBlank()||statusText.length()>1000)
      throw new ApiException(HttpStatus.BAD_REQUEST,"A short status description is required");
    return createAttempt(session,parent,image,parent.getFallbackRecipeName(),null,
        parent.getUserProblemDescription(),statusText,parent.getDesiredOutcome(),parent.getComponentKey());
  }

  @PostMapping(value="/{attemptId}/decision",consumes=MediaType.APPLICATION_JSON_VALUE)
  @Transactional
  public Response decision(@PathVariable UUID sessionId,@PathVariable UUID attemptId,
      @AuthenticationPrincipal UUID user,@RequestBody DecisionRequest request){
    ownSession(sessionId,user);RecipeRecoveryAttempt attempt=ownAttempt(sessionId,attemptId);
    if(!"RECOVERY_OFFERED".equals(attempt.getStatus()))
      throw new ApiException(HttpStatus.CONFLICT,"This attempt has no recovery/remake choice");
    if(request==null)throw new ApiException(HttpStatus.BAD_REQUEST,"A recovery decision is required");
    if("REMAKE_COMPONENT".equals(request.choice())){
      attempt.chooseRemake();
      attempts.save(attempt);
      return response(Status.REMAKE_COMPONENT,attempt,null,Decision.REMAKE_COMPONENT,null,null,
          "Bu bileşeni yeniden hazırlamak en iyi seçenek.");
    }
    if(!"TRY_RECOVERY".equals(request.choice()))
      throw new ApiException(HttpStatus.BAD_REQUEST,"Choice must be TRY_RECOVERY or REMAKE_COMPONENT");
    try{
      Action action=json.readValue(attempt.getActionJson(),Action.class);
      attempt.chooseRecovery();
      attempts.save(attempt);
      return response(Status.CORRECTION_PROPOSED,attempt,null,Decision.RECOVERABLE_BUT_MAJOR_GAP,
          action,new FollowUp(true,true,true,
          "Düzeltmeyi uygula; ardından yeni fotoğraf ve kısa durum açıklaması gönder."),
          "Küçük ve kontrollü bir düzeltme deneyebilirsin.");
    }catch(Exception e){throw new ApiException(HttpStatus.CONFLICT,"No validated recovery action is available");}
  }

  private Response createAttempt(CookingSession session,RecipeRecoveryAttempt parent,MultipartFile image,
      String recipeName,String category,String description,String statusText,String outcome,String componentKey){
    if(attempts.countBySessionIdAndCreatedAtAfter(session.getId(),Instant.now().minus(Duration.ofHours(1)))
        >=MAX_ATTEMPTS_PER_HOUR)
      throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"Recovery attempt limit reached for this cooking session");
    if(recipeName==null||recipeName.isBlank()||recipeName.trim().length()>200)
      throw new ApiException(HttpStatus.BAD_REQUEST,"Product name is required (maximum 200 characters)");
    if(description==null||description.isBlank()||description.trim().length()>1000)
      throw new ApiException(HttpStatus.BAD_REQUEST,"Problem description is required (maximum 1000 characters)");
    if(image==null||image.isEmpty())throw new ApiException(HttpStatus.BAD_REQUEST,"A photo is required");
    String mime=image.getContentType();
    if(mime==null||!List.of("image/jpeg","image/png").contains(mime))
      throw new ApiException(HttpStatus.BAD_REQUEST,"Use a JPEG or PNG photo");
    if(image.getSize()>MAX_BYTES)throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,"Image is too large");
    if(outcome!=null&&outcome.length()>500)
      throw new ApiException(HttpStatus.BAD_REQUEST,"Desired outcome is too long");
    try{
      byte[] uploaded=image.getBytes();
      BufferedImage decoded=ImageIO.read(new ByteArrayInputStream(uploaded));
      if(decoded==null)throw new ApiException(HttpStatus.BAD_REQUEST,"The image could not be decoded");
      long pixels=(long)decoded.getWidth()*decoded.getHeight();
      if(decoded.getWidth()<160||decoded.getHeight()<160||pixels>MAX_PIXELS)
        throw new ApiException(HttpStatus.BAD_REQUEST,"Image dimensions are not supported");
      ByteArrayOutputStream sanitized=new ByteArrayOutputStream();
      String format=mime.equals("image/png")?"png":"jpg";
      if(!ImageIO.write(decoded,format,sanitized))
        throw new ApiException(HttpStatus.BAD_REQUEST,"The image format is not supported");
      byte[] bytes=sanitized.toByteArray();
      String key=store(session.getUserId(),bytes,mime);
      Recipe recipe=session.getRecipeId()==null?null:recipes.findById(session.getRecipeId())
          .filter(r->r.getUserId()==null||r.getUserId().equals(session.getUserId()))
          .orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Recipe not found"));
      String component=componentKey==null||componentKey.isBlank()
          ?(parent==null?"stage-"+(session.getCurrentStage()+1):parent.getComponentKey()):componentKey.trim();
      if(component.length()>120)throw new ApiException(HttpStatus.BAD_REQUEST,"Invalid component key");
      int number=attempts.findTopBySessionIdAndComponentKeyOrderByAttemptNumberDesc(session.getId(),component)
          .map(RecipeRecoveryAttempt::getAttemptNumber).orElse(0)+1;
      RecipeRecoveryAttempt attempt=attempts.save(new RecipeRecoveryAttempt(session.getRecipeId(),
          session.getId(),parent==null?null:parent.getId(),component,session.getCurrentStage()+1,
          number,key,category,description.trim(),statusText,outcome));
      attempt.setFallbackRecipeName(recipeName.trim());
      attempts.save(attempt);
      if(parent!=null){
        parent.markFollowUpReceived();
        attempts.save(parent);
      }
      if(!vision.available()){
        log.error("Recipe recovery vision is unavailable: sessionId={}, attemptId={}, implementation={}",
            session.getId(), attempt.getId(), vision.getClass().getSimpleName());
        recordExecution(attempt,"VISION_ANALYSIS","NOT_CONFIGURED",visionModel,
            Map.of("mimeType",mime,"imageBytes",bytes.length),null,
            "PROVIDER_NOT_CONFIGURED","Vision provider is not configured",System.nanoTime());
        attempt.markUnavailable("Vision model is not configured.");
        attempts.save(attempt);
        return textOnlyFallback(attempt,recipeName,description,
            "Görsel analiz servisi kullanılamadı; yalnızca ürün adı ve sorun açıklaması değerlendirildi.");
      }
      Analysis analysis;
      long visionStarted=System.nanoTime();
      try{
        analysis=vision.analyze(bytes,mime,buildContext(recipe,session,component,recipeName.trim(),
            category,description.trim(),statusText,outcome,parent));
        recordExecution(attempt,"VISION_ANALYSIS","SUCCESS",visionModel,
            Map.of("mimeType",mime,"imageBytes",bytes.length,"componentKey",component),
            json.convertValue(analysis,Map.class),null,null,visionStarted);
      }
      catch(RecoveryAnalysisFormatException formatError){
        log.warn("Recipe recovery output could not be used: sessionId={}, attemptId={}, reason={}",
            session.getId(), attempt.getId(), formatError.getMessage());
        recordExecution(attempt,"VISION_ANALYSIS","INVALID_OUTPUT",visionModel,
            Map.of("mimeType",mime,"imageBytes",bytes.length),null,
            formatError.getClass().getSimpleName(),safeError(formatError),visionStarted);
        attempt.markAnalysisIncomplete("Vision output could not be parsed; text-only fallback was used.");
        attempts.save(attempt);
        return textOnlyFallback(attempt,recipeName,description,
            "Fotoğraf analizi güvenilir biçimde işlenemedi; yalnızca ürün adı ve sorun açıklaması değerlendirildi.");
      }
      catch(ApiException providerError){
        log.error("Recipe recovery vision provider failed: sessionId={}, attemptId={}, implementation={}, reason={}",
            session.getId(), attempt.getId(), vision.getClass().getSimpleName(),
            providerError.getMessage(), providerError);
        recordExecution(attempt,"VISION_ANALYSIS","FAILED",visionModel,
            Map.of("mimeType",mime,"imageBytes",bytes.length),null,
            providerError.getClass().getSimpleName(),safeError(providerError),visionStarted);
        attempt.markUnavailable("Vision provider request failed.");
        attempts.save(attempt);
        return textOnlyFallback(attempt,recipeName,description,
            "Fotoğraf analizinde teknik sorun yaşandı; yalnızca ürün adı ve sorun açıklaması değerlendirildi.");
      }
      if(!hasCompleteRecoveryDecision(analysis)){
        attempt.markAnalysisIncomplete("Vision output did not contain sufficient evidence; text-only fallback was used.");
        attempts.save(attempt);
        return textOnlyFallback(attempt,recipeName,description,
            "Fotoğraftan güvenilir bir değerlendirme çıkarılamadı; yalnızca ürün adı ve sorun açıklaması değerlendirildi.");
      }
      if(!RecoveryImageTrustPolicy.accepts(analysis.imageTrustScore())){
        attempt.markImageRejected(analysis.imageTrustScore(),"{\"reason\":\"IMAGE_NOT_CLEAR\"}");
        attempts.save(attempt);
        String reason=analysis.problemSummary()==null||analysis.problemSummary().isBlank()
            ?"Fotoğraf yeterince net veya ilgili değil.":"Fotoğraf net değil: "+analysis.problemSummary();
        return textOnlyFallback(attempt,recipeName,description,
            reason+" Fotoğraftan güvenilir analiz yapılamadığı için yalnızca metin açıklaması değerlendirildi.");
      }
      if(analysis.analysisConfidence().doubleValue()<0.65
          ||(analysis.targetProximityScore()>=50&&analysis.targetProximityScore()<90
              &&analysis.action().type()==ActionType.NONE)){
        attempt.applyAnalysis(analysis.imageTrustScore(),analysis.analysisConfidence(),null,
            json.writeValueAsString(Map.of("observedState",analysis.observedState())),
            json.writeValueAsString(Map.of("targetState",analysis.targetState())),
            json.writeValueAsString(Map.of("reason",analysis.reason())),null,"ANALYSIS_REJECTED");
        attempts.save(attempt);
        return textOnlyFallback(attempt,recipeName,description,
            "Fotoğraftan güvenli bir karar çıkarılamadı; yalnızca ürün adı ve sorun açıklaması değerlendirildi.");
      }
      int score=analysis.targetProximityScore();
      Set<ActionType> conservativeActions=Set.of(ActionType.REST,ActionType.MIX,ActionType.KNEAD,
          ActionType.DRAIN,ActionType.CHILL,ActionType.REFORM,ActionType.STOP_COOKING);
      if(score>=50&&score<90&&!conservativeActions.contains(analysis.action().type())){
        attempt.applyAnalysis(analysis.imageTrustScore(),analysis.analysisConfidence(),null,
            json.writeValueAsString(Map.of("observedState",analysis.observedState())),
            json.writeValueAsString(Map.of("targetState",analysis.targetState())),
            json.writeValueAsString(Map.of("reason","Önerilen işlem desteklenen güvenli işlem listesinde değil.")),
            null,"ANALYSIS_REJECTED");
        attempts.save(attempt);
        return textOnlyFallback(attempt,recipeName,description,
            "Fotoğraftan güvenli bir işlem belirlenemedi; yalnızca ürün adı ve sorun açıklaması değerlendirildi.");
      }
      RecoveryDecisionPolicy.Result policy=RecoveryDecisionPolicy.evaluate(score);
      Decision decision=policy.decision();
      Status status=policy.status();
      Action storedAction=policy.correctionRequired()||policy.recoveryChoiceRequired()?analysis.action():null;
      Action responseAction=policy.correctionRequired()?analysis.action():null;
      String lifecycle=policy.lifecycle();
      attempt.applyAnalysis(analysis.imageTrustScore(),analysis.analysisConfidence(),score,
          json.writeValueAsString(Map.of("observedState",analysis.observedState())),
          json.writeValueAsString(Map.of("targetState",analysis.targetState())),
          json.writeValueAsString(Map.of("gapSummary",analysis.gapSummary(),"reason",analysis.reason(),"decision",decision.name())),
          storedAction==null?null:json.writeValueAsString(storedAction),lifecycle);
      attempts.save(attempt);
      FollowUp follow=score>=75&&score<90?new FollowUp(true,true,true,
          "Düzeltmeyi uygula; ardından yeni fotoğraf ve kısa durum açıklaması gönder."):null;
      String message=score>=90?"Hedef kıvama yeterince yakın. Tarifin sonraki adımına geçebilirsin.":
          score>=75?"Küçük bir düzeltme gerekiyor.":score>=50?
          "Bu bileşen kurtarılabilir; ancak yeniden yapmak daha iyi sonuç verebilir.":
          "Bu bileşen makul şekilde kurtarılamıyor; yalnızca bu bileşeni yeniden hazırlamanı öneririm.";
      return response(status,attempt,analysis,decision,responseAction,follow,message);
    }catch(ApiException e){throw e;}
    catch(IOException e){throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,"Photo could not be stored");}
  }


  private void recordExecution(RecipeRecoveryAttempt attempt,String operation,String status,
      String model,Map<String,Object> requestMetadata,Object response,String errorCode,
      String errorMessage,long startedNanos){
    try{
      executions.save(new RecoveryAiExecution(attempt.getId(),operation,"RUNWARE",model,status,
          json.writeValueAsString(requestMetadata),
          response==null?null:json.writeValueAsString(response),errorCode,
          errorMessage==null?null:errorMessage.substring(0,Math.min(1000,errorMessage.length())),
          Math.max(0,(System.nanoTime()-startedNanos)/1_000_000)));
    }catch(Exception auditError){
      log.error("Recovery AI execution audit could not be persisted: attemptId={}, operation={}",
          attempt.getId(),operation,auditError);
    }
  }

  private String safeError(Exception error){
    String message=error.getMessage();
    if(message==null||message.isBlank()) return error.getClass().getSimpleName();
    return message.replaceAll("[\\r\\n\\t]"," ");
  }

  private boolean hasCompleteRecoveryDecision(Analysis analysis){
    if(analysis.imageTrustScore()==null||analysis.analysisConfidence()==null
        ||analysis.targetProximityScore()==null||analysis.action()==null
        ||analysis.action().type()==null)return false;
    if(analysis.problemCategory()==null||analysis.problemSummary()==null
        ||analysis.observedState()==null||analysis.targetState()==null
        ||analysis.gapSummary()==null||analysis.reason()==null)return false;
    return analysis.action().type()==ActionType.NONE
        ||(analysis.action().instruction()!=null&&!analysis.action().instruction().isBlank());
  }


  private Response textOnlyFallback(RecipeRecoveryAttempt attempt,String recipeName,
      String problemDescription,String reason){
    long started=System.nanoTime();
    try{
      TextFallbackAdviceService.Advice advice=textFallback.suggest(recipeName.trim(),problemDescription.trim());
      Map<String,String> payload=Map.of("advice",advice.advice(),"caution",advice.caution());
      attempt.markTextFallbackComplete(recipeName.trim(),json.writeValueAsString(payload));
      attempts.save(attempt);
      recordExecution(attempt,"TEXT_FALLBACK","SUCCESS",textModel,
          Map.of("recipeName",recipeName.trim(),"problemDescription",problemDescription.trim(),
              "trigger","VISION_UNAVAILABLE_OR_UNTRUSTED"),
          payload,null,null,started);
      return responseWithTextAdvice(attempt,reason,advice.advice(),advice.caution());
    }catch(Exception e){
      log.error("Internal text-only recovery fallback failed: attemptId={}, exceptionType={}",
          attempt.getId(),e.getClass().getSimpleName(),e);
      attempt.markTextFallbackUnavailable();
      attempts.save(attempt);
      recordExecution(attempt,"TEXT_FALLBACK","FAILED",textModel,
          Map.of("recipeName",recipeName.trim(),"trigger","VISION_UNAVAILABLE_OR_UNTRUSTED"),
          null,e.getClass().getSimpleName(),safeError(e),started);
      return responseWithTextAdvice(attempt,
          reason+" Metin değerlendirmesi de şu anda üretilemedi.",null,
          "Lütfen daha sonra tekrar dene. Bu kayıt yardım geçmişinde saklandı.");
    }
  }

  private Response responseWithTextAdvice(RecipeRecoveryAttempt attempt,String message,
      String advice,String caution){
    return new Response(Status.ANALYSIS_INCOMPLETE,attempt.getImageTrustScore(),
        attempt.getAnalysisConfidence(),attempt.getTargetProximityScore(),null,null,null,null,
        null,attempt.getId(),message,new TextAdvice(advice,caution));
  }

  private String buildContext(Recipe recipe,CookingSession session,String component,String recipeName,
      String category,String description,String statusText,String outcome,RecipeRecoveryAttempt parent){
    try{
      Map<String,Object> c=new LinkedHashMap<>();
      c.put("componentKey",component);c.put("productName",recipeName);
      c.put("userProblemDescription",description);c.put("stageOrder",session.getCurrentStage()+1);
      c.put("problemCategory",category);c.put("problemDescription",description);
      c.put("followUpStatus",statusText);c.put("desiredOutcome",outcome);
      if(recipe!=null){c.put("recipeTitle",recipe.getTitle());c.put("productType",recipe.getProductType());
        c.put("ingredients",json.readTree(recipe.getIngredientsJson()));c.put("stages",json.readTree(recipe.getStagesJson()));}
      if(parent!=null){
        List<Map<String,Object>> history=new ArrayList<>();
        RecipeRecoveryAttempt cursor=parent;
        int depth=0;
        while(cursor!=null&&depth++<6){
          Map<String,Object> previous=new LinkedHashMap<>();
          previous.put("status",cursor.getStatus());
          previous.put("observation",cursor.getObservedStateJson());
          previous.put("target",cursor.getTargetStateJson());
          previous.put("assessment",cursor.getAssessmentJson());
          previous.put("confirmedAction",cursor.getActionJson());
          previous.put("actionConfirmed",cursor.getUserActionConfirmedAt()!=null);
          history.add(previous);
          cursor=cursor.getParentAttemptId()==null?null:
              attempts.findById(cursor.getParentAttemptId()).orElse(null);
        }
        c.put("previousAttempts",history);
      }
      return json.writeValueAsString(c);
    }catch(Exception e){throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,"Recipe context could not be prepared");}
  }

  private String store(UUID user,byte[] bytes,String mime)throws IOException{
    Path root=storage.toAbsolutePath().normalize();
    Path dir=root.resolve(user.toString()).normalize();
    if(!dir.startsWith(root))throw new IOException("Invalid storage path");
    Files.createDirectories(dir);
    try{Files.setPosixFilePermissions(dir,java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));}
    catch(UnsupportedOperationException ignored){}
    String key=user+"/"+UUID.randomUUID()+(mime.equals("image/png")?".png":".jpg");
    Path target=root.resolve(key).normalize();
    if(!target.startsWith(root))throw new IOException("Invalid storage path");
    Files.write(target,bytes,StandardOpenOption.CREATE_NEW);
    try{Files.setPosixFilePermissions(target,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));}
    catch(UnsupportedOperationException ignored){}
    return key;
  }
  private CookingSession ownSession(UUID id,UUID user){
    CookingSession session=sessions.findById(id).filter(s->s.getUserId().equals(user))
        .orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Session not found"));
    if(!"ACTIVE".equals(session.getStatus()))
      throw new ApiException(HttpStatus.CONFLICT,"Cooking session is not active");
    return session;
  }
  private RecipeRecoveryAttempt ownAttempt(UUID session,UUID id){
    return attempts.findById(id).filter(a->session.equals(a.getSessionId()))
        .orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Recovery attempt not found"));
  }
  private Response response(Status status,RecipeRecoveryAttempt attempt,Analysis analysis,Decision decision,
      Action action,FollowUp follow,String message){
    Problem problem=analysis==null?null:new Problem(analysis.problemCategory(),analysis.problemSummary());
    Assessment assessment=analysis==null?null:new Assessment(analysis.targetState(),analysis.gapSummary(),analysis.reason());
    return new Response(status,
        analysis==null?attempt.getImageTrustScore():analysis.imageTrustScore(),
        analysis==null?null:analysis.analysisConfidence(),
        analysis==null?attempt.getTargetProximityScore():analysis.targetProximityScore(),
        problem,assessment,decision,action,follow,attempt.getId(),message);
  }
}
