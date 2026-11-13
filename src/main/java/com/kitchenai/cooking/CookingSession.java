package com.kitchenai.cooking;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
public class CookingSession {
  @Id private UUID id;
  private UUID userId;
  private UUID recipeId;
  private String sessionType;
  private String status;
  private int currentStage;
  private String desiredOutcome;
  @Column(columnDefinition = "TEXT") private String contextJson;
  private Instant startedAt;
  private Instant completedAt;
  public CookingSession() {}
  public CookingSession(UUID u,UUID r,String type,String outcome){
    id=UUID.randomUUID();userId=u;recipeId=r;sessionType=type;status="ACTIVE";
    desiredOutcome=outcome;startedAt=Instant.now();
  }
  public UUID getId(){return id;}
  public UUID getUserId(){return userId;}
  public UUID getRecipeId(){return recipeId;}
  public String getStatus(){return status;}
  public String getSessionType(){return sessionType;}
  public String getDesiredOutcome(){return desiredOutcome;}
  public int getCurrentStage(){return currentStage;}
  public Instant getStartedAt(){return startedAt;}
  public Instant getCompletedAt(){return completedAt;}
  public void complete(){status="COMPLETED";completedAt=Instant.now();}
  public void abandon(){status="ABANDONED";completedAt=Instant.now();}
}
