package com.kitchenai.zerowaste;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZeroWasteQuantityRulesTest {
  @Test void scalesServingsWithoutFloatingPointArtifacts() {
    var result = ZeroWasteQuantityRules.scale(new BigDecimal("150"), 3, 2);
    assertEquals(new BigDecimal("225.000"), result.quantity());
    assertEquals("EXACT", result.certainty());
  }

  @Test void marksRoundedScaleAsEstimated() {
    var result = ZeroWasteQuantityRules.scale(new BigDecimal("1"), 1, 3);
    assertEquals(new BigDecimal("0.333"), result.quantity());
    assertEquals("ESTIMATED", result.certainty());
  }

  @Test void rejectsNonPositiveQuantityAndServingCounts() {
    assertThrows(IllegalArgumentException.class,
        () -> ZeroWasteQuantityRules.scale(new BigDecimal("0"), 2, 2));
    assertThrows(IllegalArgumentException.class,
        () -> ZeroWasteQuantityRules.scale(BigDecimal.ONE, 0, 2));
  }

  @Test void onlyConvertsWithinKnownDimensions() {
    assertEquals(new BigDecimal("1000"), ZeroWasteQuantityRules.convert(
        BigDecimal.ONE, "kg", "g"));
    assertFalse(ZeroWasteQuantityRules.compatible("ml", "g"));
    assertThrows(RuntimeException.class, () -> ZeroWasteQuantityRules.convert(
        BigDecimal.ONE, "su bardağı", "ml"));
  }

  @Test void normalizesTurkishIngredientNamesConservatively() {
    assertEquals("yumurta aki", ZeroWasteQuantityRules.normalize("Yumurta Akı"));
    assertEquals("cilek", ZeroWasteQuantityRules.normalize("Çilek"));
  }
}
