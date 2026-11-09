package com.kitchenai.recovery;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RecoveryDecisionPolicyTest {
  @Test void scoreBoundariesAreInclusiveAsSpecified() {
    assertEquals("REMAKE_RECOMMENDED",RecoveryDecisionPolicy.evaluate(0).lifecycle());
    assertEquals("REMAKE_RECOMMENDED",RecoveryDecisionPolicy.evaluate(49).lifecycle());
    assertEquals("RECOVERY_OFFERED",RecoveryDecisionPolicy.evaluate(50).lifecycle());
    assertEquals("RECOVERY_OFFERED",RecoveryDecisionPolicy.evaluate(74).lifecycle());
    assertEquals("CORRECTION_PROPOSED",RecoveryDecisionPolicy.evaluate(75).lifecycle());
    assertEquals("CORRECTION_PROPOSED",RecoveryDecisionPolicy.evaluate(89).lifecycle());
    assertEquals("READY_TO_CONTINUE",RecoveryDecisionPolicy.evaluate(90).lifecycle());
    assertEquals("READY_TO_CONTINUE",RecoveryDecisionPolicy.evaluate(100).lifecycle());
  }
  @Test void scoresOutsideRangeAreRejected() {
    assertThrows(IllegalArgumentException.class,()->RecoveryDecisionPolicy.evaluate(-1));
    assertThrows(IllegalArgumentException.class,()->RecoveryDecisionPolicy.evaluate(101));
  }
}