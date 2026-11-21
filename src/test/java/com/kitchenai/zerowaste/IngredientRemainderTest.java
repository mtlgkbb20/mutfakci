package com.kitchenai.zerowaste;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IngredientRemainderTest {
  private IngredientRemainder remainder(String quantity) {
    return new IngredientRemainder(UUID.randomUUID(), "kek", null, null, null, "Yumurta akı", "yumurta aki",
        new BigDecimal(quantity), "adet", "EXACT", null);
  }

  @Test
  void partialConsumptionKeepsItAvailable() {
    var r = remainder("4");
    assertEquals(0, new BigDecimal("4").compareTo(r.consume(new BigDecimal("1.5"))));
    assertEquals(0, new BigDecimal("2.5").compareTo(r.getQuantity()));
    assertEquals("AVAILABLE", r.getStatus());
  }

  @Test
  void consumingEverythingMarksItConsumed() {
    var r = remainder("2");
    r.consume(new BigDecimal("2.000"));
    assertEquals("CONSUMED", r.getStatus());
    assertEquals(0, r.getQuantity().signum());
  }

  @Test
  void overConsumptionIsRejectedWithoutChangingQuantity() {
    var r = remainder("2");
    assertThrows(IllegalArgumentException.class, () -> r.consume(new BigDecimal("2.001")));
    assertEquals(0, new BigDecimal("2").compareTo(r.getQuantity()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"TRANSFERRED", "DISCARDED"})
  void availableRemainderCanBeTransferredOrDiscardedOnce(String next) {
    var r = remainder("1");
    r.transition(next);
    assertEquals(next, r.getStatus());
    assertThrows(IllegalStateException.class, () -> r.transition("DISCARDED"));
  }

  @Test
  void unknownTransitionsAreRejected() {
    var r = remainder("1");
    assertThrows(IllegalArgumentException.class, () -> r.transition("CONSUMED"));
    assertThrows(IllegalArgumentException.class, () -> r.transition("AVAILABLE"));
    assertEquals("AVAILABLE", r.getStatus());
  }
}
