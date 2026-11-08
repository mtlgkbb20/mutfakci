package com.kitchenai.recovery;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recipe_recovery_attempt")
public class RecipeRecoveryAttempt {
  @Id private UUID id;
  private UUID recipeId;
  private UUID sessionId;
  private UUID parentAttemptId;
  @Column(length = 120) private String componentKey;
  private Integer stageOrder;
  private int attemptNumber;
  @Column(length = 500) private String imageObjectKey;
  @Column(length = 40) private String userProblemCategory;
  @Column(columnDefinition = "TEXT") private String userProblemDescription;
  @Column(columnDefinition = "TEXT") private String userStatusText;
  @Column(columnDefinition = "TEXT") private String desiredOutcome;
  private Integer imageTrustScore;
  @Column(precision = 4, scale = 3) private BigDecimal analysisConfidence;
  private Integer targetProximityScore;
  @Column(columnDefinition = "TEXT") private String observedStateJson;
  @Column(columnDefinition = "TEXT") private String targetStateJson;
  @Column(columnDefinition = "TEXT") private String assessmentJson;
  @Column(columnDefinition = "TEXT") private String actionJson;
  @Column(length = 40) private String status;
  private Instant userActionConfirmedAt;
  private Instant createdAt;

  protected RecipeRecoveryAttempt() {}
  public RecipeRecoveryAttempt(UUID recipeId, UUID sessionId, UUID parentAttemptId,
      String componentKey, Integer stageOrder, int attemptNumber, String imageObjectKey,
      String problemCategory, String problemDescription, String statusText, String desiredOutcome) {
    id=UUID.randomUUID(); this.recipeId=recipeId; this.sessionId=sessionId;
    this.parentAttemptId=parentAttemptId; this.componentKey=componentKey; this.stageOrder=stageOrder;
    this.attemptNumber=attemptNumber; this.imageObjectKey=imageObjectKey;
    this.userProblemCategory=problemCategory; this.userProblemDescription=problemDescription;
    this.userStatusText=statusText; this.desiredOutcome=desiredOutcome;
    status="ANALYZING"; createdAt=Instant.now();
  }
  public UUID getId(){return id;}
  public UUID getRecipeId(){return recipeId;}
  public UUID getSessionId(){return sessionId;}
  public UUID getParentAttemptId(){return parentAttemptId;}
  public String getComponentKey(){return componentKey;}
  public int getAttemptNumber(){return attemptNumber;}
  public String getStatus(){return status;}
  public Integer getImageTrustScore(){return imageTrustScore;}
  public Integer getTargetProximityScore(){return targetProximityScore;}
  public Instant getUserActionConfirmedAt(){return userActionConfirmedAt;}
  public String getDesiredOutcome(){return desiredOutcome;}
  public String getActionJson(){return actionJson;}
  public String getObservedStateJson(){return observedStateJson;}
  public String getTargetStateJson(){return targetStateJson;}
  public String getAssessmentJson(){return assessmentJson;}
  public Instant getCreatedAt(){return createdAt;}
  public void markImageRejected(int trust,String observed){imageTrustScore=trust;observedStateJson=observed;status="IMAGE_REJECTED";}
  public void markUnavailable(String reason){status="VISION_PROVIDER_UNAVAILABLE";assessmentJson=reason;}
  public void applyAnalysis(int trust,BigDecimal confidence,Integer proximity,String observed,
      String target,String assessment,String action,String newStatus){
    imageTrustScore=trust;analysisConfidence=confidence;targetProximityScore=proximity;
    observedStateJson=observed;targetStateJson=target;assessmentJson=assessment;
    actionJson=action;status=newStatus;
  }
  public void confirmAction(){
    if(!"CORRECTION_PROPOSED".equals(status)) throw new IllegalStateException("No pending correction");
    userActionConfirmedAt=Instant.now();status="AWAITING_FOLLOW_UP";
  }
  public void markFollowUpReceived(){
    if(!"AWAITING_FOLLOW_UP".equals(status)&&!"IMAGE_REJECTED".equals(status)
        &&!"ANALYSIS_REJECTED".equals(status))
      throw new IllegalStateException("Attempt cannot receive another follow-up");
    status="FOLLOW_UP_RECEIVED";
  }
}