package com.kitchenai.pantry;

import com.kitchenai.common.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PantryRemainderTransferService {
  private final PantryItemRepository items;
  private final PantryMovementRepository movements;
  private final PantryOperationRepository operations;

  public PantryRemainderTransferService(PantryItemRepository items,
      PantryMovementRepository movements, PantryOperationRepository operations) {
    this.items = items; this.movements = movements; this.operations = operations;
  }

  @Transactional
  public UUID transfer(UUID userId, String name, String normalizedName, BigDecimal quantity,
      String unit, LocalDate expiryDate, String operationKey) {
    if (operations.existsByUserIdAndOperationKey(userId, operationKey)) {
      return movements.findFirstByUserIdAndOperationKey(userId, operationKey)
          .map(PantryMovement::getPantryItemId)
          .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Pantry transfer operation already exists."));
    }
    PantryItem item = items.lockFirstByUserAndNormalizedName(userId, normalizedName).orElse(null);
    BigDecimal delta = quantity;
    if (item == null) {
      item = new PantryItem(userId, name, normalizedName, "OTHER", quantity, unit, expiryDate);
      items.save(item);
    } else {
      if (!compatible(item.getUnit(), unit))
        throw new ApiException(HttpStatus.BAD_REQUEST, "Pantry birimi ile artan malzeme birimi uyumlu değil.");
      delta = convert(quantity, unit, item.getUnit());
      item.adjust(delta);
      if (item.getExpiryDate() == null && expiryDate != null) item.update(item.getName(),
          item.getNormalizedName(), item.getCategory(), item.getQuantity(), item.getUnit(), expiryDate);
      items.save(item);
    }
    operations.save(new PantryOperation(userId, operationKey));
    movements.save(new PantryMovement(userId, item.getId(), "ADD", delta, item.getUnit(),
        "Zero Waste remainder transfer", operationKey));
    return item.getId();
  }

  private static boolean compatible(String a, String b) {
    return dimension(a).equals(dimension(b));
  }
  private static String dimension(String unit) {
    if ("g".equals(unit) || "kg".equals(unit)) return "MASS";
    if ("ml".equals(unit) || "l".equals(unit)) return "VOLUME";
    if ("piece".equals(unit) || "adet".equals(unit)) return "COUNT";
    return "CUSTOM:" + unit;
  }
  private static BigDecimal convert(BigDecimal value, String from, String to) {
    if (from.equals(to)) return value;
    if ("kg".equals(from) && "g".equals(to)) return value.multiply(BigDecimal.valueOf(1000));
    if ("g".equals(from) && "kg".equals(to)) return value.divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP);
    if ("l".equals(from) && "ml".equals(to)) return value.multiply(BigDecimal.valueOf(1000));
    if ("ml".equals(from) && "l".equals(to)) return value.divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP);
    if (compatible(from, to)) return value;
    throw new ApiException(HttpStatus.BAD_REQUEST, "Pantry birimi dönüştürülemiyor.");
  }
}
