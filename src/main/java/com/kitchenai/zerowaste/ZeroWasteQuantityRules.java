package com.kitchenai.zerowaste;

import com.kitchenai.common.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.Locale;
import org.springframework.http.HttpStatus;

public final class ZeroWasteQuantityRules {
  public static final int QUANTITY_SCALE = 3;
  private ZeroWasteQuantityRules() {}

  public record ScaledQuantity(BigDecimal quantity, String certainty) {}

  public static ScaledQuantity scale(BigDecimal base, int requestedServings, int defaultServings) {
    if (base == null || base.signum() <= 0 || requestedServings < 1 || defaultServings < 1)
      throw new IllegalArgumentException("Quantity and serving counts must be positive");
    BigDecimal exact = base.multiply(BigDecimal.valueOf(requestedServings))
        .divide(BigDecimal.valueOf(defaultServings), 8, RoundingMode.HALF_UP);
    BigDecimal rounded = exact.setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
    return new ScaledQuantity(rounded, exact.compareTo(rounded) == 0 ? "EXACT" : "ESTIMATED");
  }

  public static String normalize(String value) {
    if (value == null) return "";
    String lower = value.trim().toLowerCase(Locale.forLanguageTag("tr-TR")).replace('ı', 'i');
    return Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
        .replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
  }

  public static String validateUnit(String value) {
    if (value == null || value.isBlank() || value.trim().length() > 20)
      throw new ApiException(HttpStatus.BAD_REQUEST, "Geçersiz birim.");
    return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
  }

  public static boolean compatible(String left, String right) {
    return dimension(left).equals(dimension(right));
  }

  public static BigDecimal convert(BigDecimal quantity, String from, String to) {
    if (!compatible(from, to)) throw new ApiException(HttpStatus.BAD_REQUEST, "Birimler dönüştürülemiyor.");
    if (from.equals(to)) return quantity;
    if (from.equals("kg") && to.equals("g")) return quantity.multiply(BigDecimal.valueOf(1000));
    if (from.equals("g") && to.equals("kg")) return quantity.divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP);
    if (from.equals("l") && to.equals("ml")) return quantity.multiply(BigDecimal.valueOf(1000));
    if (from.equals("ml") && to.equals("l")) return quantity.divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP);
    if ("COUNT".equals(dimension(from)) && "COUNT".equals(dimension(to))) return quantity;
    throw new ApiException(HttpStatus.BAD_REQUEST, "Bu birimler için güvenli dönüşüm tanımlı değil.");
  }

  private static String dimension(String unit) {
    if ("g".equals(unit) || "kg".equals(unit)) return "MASS";
    if ("ml".equals(unit) || "l".equals(unit)) return "VOLUME";
    if ("adet".equals(unit) || "piece".equals(unit)) return "COUNT";
    return "CUSTOM:" + unit;
  }
}
