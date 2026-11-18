package com.kitchenai.zerowaste;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZeroWasteByProductCalculatorTest {
  @Test void derivesOneToOneRemainderFromFourEggYolks() {
    var result = ZeroWasteByProductCalculator.derive(new BigDecimal("4.000"), 1, 1, "EXACT");
    assertEquals(new BigDecimal("4.000"), result.quantity());
    assertEquals("EXACT", result.certainty());
  }

  @Test void marksFractionalDerivedQuantityAsEstimated() {
    var result = ZeroWasteByProductCalculator.derive(new BigDecimal("1.000"), 1, 3, "EXACT");
    assertEquals(new BigDecimal("0.333"), result.quantity());
    assertEquals("ESTIMATED", result.certainty());
  }

  @Test void propagatesEstimatedSourceCertainty() {
    var result = ZeroWasteByProductCalculator.derive(new BigDecimal("4.000"), 1, 1, "ESTIMATED");
    assertEquals("ESTIMATED", result.certainty());
  }

  @Test void rejectsInvalidRatios() {
    assertThrows(IllegalArgumentException.class,
        () -> ZeroWasteByProductCalculator.derive(BigDecimal.ONE, 1, 0, "EXACT"));
  }
}
