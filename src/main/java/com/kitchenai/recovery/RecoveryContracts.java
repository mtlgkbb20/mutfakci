package com.kitchenai.recovery;

import java.math.BigDecimal;
import java.util.UUID;

public final class RecoveryContracts {
  private RecoveryContracts() {}
  public enum Status {
    REQUEST_NEW_IMAGE, NEEDS_CLARIFICATION, CORRECTION_PROPOSED,
    OFFER_RECOVERY_OR_REMAKE, READY_TO_CONTINUE, REMAKE_COMPONENT,
    VISION_PROVIDER_UNAVAILABLE, ANALYSIS_INCOMPLETE
  }
  public enum ActionType {
    REST, MIX, KNEAD, ADD_INGREDIENT, ADJUST_TEMPERATURE, DRAIN,
    CHILL, REFORM, CONTINUE_COOKING, STOP_COOKING, REMAKE_COMPONENT, NONE
  }
  public enum Decision {
    READY_TO_CONTINUE, MINOR_CORRECTION, RECOVERABLE_BUT_MAJOR_GAP, REMAKE_COMPONENT
  }
  public record Action(ActionType type, String instruction, BigDecimal quantity,
      String unit, Integer durationSeconds) {}
  public record Problem(String category, String summary) {}
  public record Assessment(String targetState, String gapSummary, String reason) {}
  public record FollowUp(boolean required, boolean requestPhoto,
      boolean requestStatusText, String prompt) {}
  public record Response(Status status, Integer imageTrustScore, BigDecimal analysisConfidence,
      Integer targetProximityScore, Problem problem, Assessment assessment,
      Decision decision, Action action, FollowUp followUp, UUID attemptId,
      String message) {}
  public record Analysis(int imageTrustScore, BigDecimal analysisConfidence,
      String problemCategory, String problemSummary, String observedState,
      String targetState, String gapSummary, String reason,
      int targetProximityScore, Action action) {}
  public record ConfirmRequest(boolean confirmed, String note) {}
  public record DecisionRequest(String choice) {}
}
