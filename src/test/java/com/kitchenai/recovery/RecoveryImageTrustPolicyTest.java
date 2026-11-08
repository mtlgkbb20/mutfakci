package com.kitchenai.recovery;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class RecoveryImageTrustPolicyTest {
  @Test void sixtyPassesAndFiftyNineFails() {
    assertFalse(RecoveryImageTrustPolicy.accepts(59));
    assertTrue(RecoveryImageTrustPolicy.accepts(60));
    assertTrue(RecoveryImageTrustPolicy.accepts(100));
  }
  @Test void outOfRangeTrustIsRejected() {
    assertThrows(IllegalArgumentException.class,()->RecoveryImageTrustPolicy.accepts(-1));
    assertThrows(IllegalArgumentException.class,()->RecoveryImageTrustPolicy.accepts(101));
  }
}