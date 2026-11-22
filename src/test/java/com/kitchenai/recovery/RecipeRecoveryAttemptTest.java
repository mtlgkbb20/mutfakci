package com.kitchenai.recovery;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RecipeRecoveryAttemptTest {
  private RecipeRecoveryAttempt attempt(String status, String action) {
    var a = new RecipeRecoveryAttempt(null, UUID.randomUUID(), null, "stage-1", 1, 1, "k", null, "d", null, null);
    a.applyAnalysis(80, new BigDecimal("0.9"), 60, "{}", "{}", "{}", action, status);
    return a;
  }

  @Test
  void newAttemptStartsAnalyzing() {
    assertEquals("ANALYZING", new RecipeRecoveryAttempt(null, UUID.randomUUID(), null, "c", 1, 1, "k", null, "d", null, null).getStatus());
  }

  @Test
  void onlyAProposedCorrectionCanBeConfirmed() {
    var proposed = attempt("CORRECTION_PROPOSED", "{}");
    proposed.confirmAction();
    assertEquals("AWAITING_FOLLOW_UP", proposed.getStatus());
    assertNotNull(proposed.getUserActionConfirmedAt());
    assertThrows(IllegalStateException.class, proposed::confirmAction);
    assertThrows(IllegalStateException.class, () -> attempt("READY_TO_CONTINUE", null).confirmAction());
  }

  @ParameterizedTest
  @ValueSource(strings = {"AWAITING_FOLLOW_UP", "IMAGE_REJECTED", "ANALYSIS_REJECTED"})
  void followUpIsAcceptedFromWaitingOrRejectedStates(String status) {
    var a = attempt(status, null);
    a.markFollowUpReceived();
    assertEquals("FOLLOW_UP_RECEIVED", a.getStatus());
    assertThrows(IllegalStateException.class, a::markFollowUpReceived);
  }

  @Test
  void recoveryChoiceNeedsAnOfferAndAStoredAction() {
    var offered = attempt("RECOVERY_OFFERED", "{\"type\":\"MIX\"}");
    offered.chooseRecovery();
    assertEquals("CORRECTION_PROPOSED", offered.getStatus());
    assertThrows(IllegalStateException.class, () -> attempt("RECOVERY_OFFERED", null).chooseRecovery());
    assertThrows(IllegalStateException.class, () -> attempt("READY_TO_CONTINUE", "{}").chooseRecovery());
  }

  @Test
  void remakeChoiceClearsTheStoredAction() {
    var offered = attempt("RECOVERY_OFFERED", "{\"type\":\"MIX\"}");
    offered.chooseRemake();
    assertEquals("REMAKE_RECOMMENDED", offered.getStatus());
    assertNull(offered.getActionJson());
    assertThrows(IllegalStateException.class, offered::chooseRemake);
  }

  @Test
  void imageRejectionRecordsTrustScore() {
    var a = attempt("ANALYZING", null);
    a.markImageRejected(30, "{\"reason\":\"IMAGE_NOT_CLEAR\"}");
    assertEquals("IMAGE_REJECTED", a.getStatus());
    assertEquals(30, a.getImageTrustScore());
  }
}
