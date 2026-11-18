package com.kitchenai.recovery;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recovery_ai_execution")
public class RecoveryAiExecution {
  @Id private UUID id;
  private UUID attemptId;
  @Column(length=40) private String operation;
  @Column(length=40) private String provider;
  @Column(length=160) private String model;
  @Column(length=30) private String status;
  @Column(columnDefinition="TEXT") private String requestMetadataJson;
  @Column(columnDefinition="TEXT") private String responseJson;
  @Column(length=80) private String errorCode;
  @Column(length=1000) private String errorMessage;
  private Long durationMs;
  private Instant createdAt;

  protected RecoveryAiExecution() {}

  public RecoveryAiExecution(UUID attemptId, String operation, String provider, String model,
      String status, String requestMetadataJson, String responseJson, String errorCode,
      String errorMessage, Long durationMs) {
    this.id=UUID.randomUUID();
    this.attemptId=attemptId;
    this.operation=operation;
    this.provider=provider;
    this.model=model;
    this.status=status;
    this.requestMetadataJson=requestMetadataJson;
    this.responseJson=responseJson;
    this.errorCode=errorCode;
    this.errorMessage=errorMessage;
    this.durationMs=durationMs;
    this.createdAt=Instant.now();
  }
  public UUID getId(){return id;}
  public UUID getAttemptId(){return attemptId;}
  public String getOperation(){return operation;}
  public String getProvider(){return provider;}
  public String getModel(){return model;}
  public String getStatus(){return status;}
  public String getRequestMetadataJson(){return requestMetadataJson;}
  public String getResponseJson(){return responseJson;}
  public String getErrorCode(){return errorCode;}
  public String getErrorMessage(){return errorMessage;}
  public Long getDurationMs(){return durationMs;}
  public Instant getCreatedAt(){return createdAt;}
}
