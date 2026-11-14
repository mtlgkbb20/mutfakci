package com.kitchenai.pantry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PantryInventoryService {
  public record StockLine(String name, BigDecimal quantity, String unit, LocalDate expiryDate) {}
  public record PurchaseLine(String name, String category, BigDecimal quantity, String unit) {}

  private final PantryItemRepository items;
  private final PantryMovementRepository movements;
  private final PantryOperationRepository operations;

  public PantryInventoryService(PantryItemRepository items, PantryMovementRepository movements,
      PantryOperationRepository operations) {
    this.items = items;
    this.movements = movements;
    this.operations = operations;
  }

  @Transactional(readOnly = true)
  public List<StockLine> activeStock(UUID userId) {
    return items.findByUserIdAndArchivedFalseOrderByCategoryAscNameAsc(userId).stream()
        .map(item -> new StockLine(item.getName(), item.getQuantity(), item.getUnit(), item.getExpiryDate()))
        .toList();
  }

  /** Applies a confirmed grocery purchase. Caller and this method participate in one transaction. */
  @Transactional
  public boolean applyPurchase(UUID userId, String operationKey, List<PurchaseLine> lines) {
    if (operations.existsByUserIdAndOperationKey(userId, operationKey)) return false;
    if (lines == null || lines.isEmpty()) throw new IllegalArgumentException("At least one purchased line is required.");

    // Persist the idempotency guard in the same transaction as all stock movements.
    operations.saveAndFlush(new PantryOperation(userId, operationKey));
    for (PurchaseLine line : lines) {
      if (line.quantity() == null || line.quantity().signum() <= 0) {
        throw new IllegalArgumentException("Purchased quantities must be positive.");
      }
      String normalized = normalize(line.name());
      String unit = normalizeUnit(line.unit());
      PantryItem existing = items.findFirstByUserIdAndArchivedFalseAndNormalizedName(userId, normalized)
          .filter(item -> compatible(item.getUnit(), unit)).orElse(null);
      if (existing == null) {
        PantryItem created = items.save(new PantryItem(userId, line.name().trim(), normalized,
            category(line.category()), line.quantity(), unit, null));
        movements.save(new PantryMovement(userId, created.getId(), "ADD", line.quantity(), unit,
            "Confirmed grocery purchase", operationKey));
      } else {
        BigDecimal amount = convert(line.quantity(), unit, existing.getUnit());
        existing.adjust(amount);
        items.save(existing);
        movements.save(new PantryMovement(userId, existing.getId(), "ADD", amount, existing.getUnit(),
            "Confirmed grocery purchase", operationKey));
      }
    }
    return true;
  }

  private static String normalize(String value) {
    return Normalizer.normalize(value == null ? "" : value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "").replaceAll("[^a-z0-9 ]", "").replaceAll("\\s+", " ");
  }
  private static String category(String value) {
    return value == null || value.isBlank() ? "OTHER" : value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_");
  }
  private static String normalizeUnit(String value) {
    String unit = value.trim().toLowerCase(Locale.ROOT);
    if (unit.equals("pcs") || unit.equals("adet")) return "piece";
    return unit;
  }
  private static boolean compatible(String a, String b) {
    return dimension(a).equals(dimension(b));
  }
  private static String dimension(String unit) {
    return switch (unit) {
      case "g", "kg" -> "MASS";
      case "ml", "l" -> "VOLUME";
      case "piece", "adet", "pcs" -> "COUNT";
      default -> "CUSTOM:" + unit;
    };
  }
  private static BigDecimal convert(BigDecimal quantity, String from, String to) {
    if (from.equals(to)) return quantity;
    if (from.equals("kg") && to.equals("g")) return quantity.multiply(BigDecimal.valueOf(1000));
    if (from.equals("g") && to.equals("kg")) return quantity.divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP);
    if (from.equals("l") && to.equals("ml")) return quantity.multiply(BigDecimal.valueOf(1000));
    if (from.equals("ml") && to.equals("l")) return quantity.divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP);
    return quantity;
  }
}
