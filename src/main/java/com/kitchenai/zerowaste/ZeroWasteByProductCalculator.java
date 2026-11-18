package com.kitchenai.zerowaste;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ZeroWasteByProductCalculator {
  private ZeroWasteByProductCalculator() {}

  public static ZeroWasteQuantityRules.ScaledQuantity derive(
      BigDecimal scaledSourceQuantity, int numerator, int denominator, String sourceCertainty) {
    if (scaledSourceQuantity == null || scaledSourceQuantity.signum() <= 0
        || numerator <= 0 || denominator <= 0) {
      throw new IllegalArgumentException("By-product rule quantities and ratio must be positive");
    }
    BigDecimal exact = scaledSourceQuantity.multiply(BigDecimal.valueOf(numerator))
        .divide(BigDecimal.valueOf(denominator), 8, RoundingMode.HALF_UP);
    BigDecimal rounded = exact.setScale(ZeroWasteQuantityRules.QUANTITY_SCALE, RoundingMode.HALF_UP);
    boolean exactResult = "EXACT".equals(sourceCertainty) && exact.compareTo(rounded) == 0;
    return new ZeroWasteQuantityRules.ScaledQuantity(rounded, exactResult ? "EXACT" : "ESTIMATED");
  }
}
