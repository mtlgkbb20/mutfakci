package com.kitchenai.recovery;

import com.kitchenai.recovery.RecoveryContracts.Decision;
import com.kitchenai.recovery.RecoveryContracts.Status;

public final class RecoveryDecisionPolicy {
  private RecoveryDecisionPolicy() {}
  public record Result(Decision decision, Status status, String lifecycle,
      boolean correctionRequired, boolean recoveryChoiceRequired) {}
  public static Result evaluate(int score) {
    if(score<0||score>100) throw new IllegalArgumentException("Score must be between 0 and 100");
    if(score>=90) return new Result(Decision.READY_TO_CONTINUE,Status.READY_TO_CONTINUE,
        "READY_TO_CONTINUE",false,false);
    if(score>=75) return new Result(Decision.MINOR_CORRECTION,Status.CORRECTION_PROPOSED,
        "CORRECTION_PROPOSED",true,false);
    if(score>=50) return new Result(Decision.RECOVERABLE_BUT_MAJOR_GAP,
        Status.OFFER_RECOVERY_OR_REMAKE,"RECOVERY_OFFERED",false,true);
    return new Result(Decision.REMAKE_COMPONENT,Status.REMAKE_COMPONENT,
        "REMAKE_RECOMMENDED",false,false);
  }
}