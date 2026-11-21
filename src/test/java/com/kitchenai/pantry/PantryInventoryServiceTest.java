package com.kitchenai.pantry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.kitchenai.pantry.PantryInventoryService.PurchaseLine;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

class PantryInventoryServiceTest {
  private final PantryItemRepository items = mock(PantryItemRepository.class);
  private final PantryMovementRepository movements = mock(PantryMovementRepository.class);
  private final PantryOperationRepository operations = mock(PantryOperationRepository.class);
  private final PantryInventoryService service = new PantryInventoryService(items, movements, operations);
  private final UUID user = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    when(items.save(any(PantryItem.class))).thenAnswer(inv -> inv.getArgument(0));
    when(items.findFirstByUserIdAndArchivedFalseAndNormalizedName(any(), any())).thenReturn(Optional.empty());
  }

  private PantryItem existing(String name, String normalized, String quantity, String unit) {
    PantryItem item = new PantryItem(user, name, normalized, "VEGETABLE", new BigDecimal(quantity), unit, null);
    when(items.findFirstByUserIdAndArchivedFalseAndNormalizedName(user, normalized)).thenReturn(Optional.of(item));
    return item;
  }

  @Test
  void activeStockMapsOwnedItems() {
    PantryItem item = new PantryItem(user, "Süt", "sut", "DAIRY", new BigDecimal("1"), "l", LocalDate.of(2026, 12, 1));
    when(items.findByUserIdAndArchivedFalseOrderByCategoryAscNameAsc(user)).thenReturn(List.of(item));
    assertEquals(List.of(new PantryInventoryService.StockLine("Süt", new BigDecimal("1"), "l", LocalDate.of(2026, 12, 1))),
        service.activeStock(user));
  }

  @Test
  void repeatedPurchaseOperationIsIgnored() {
    when(operations.existsByUserIdAndOperationKey(user, "op-1")).thenReturn(true);
    assertFalse(service.applyPurchase(user, "op-1", List.of(new PurchaseLine("Süt", "DAIRY", BigDecimal.ONE, "l"))));
    verify(items, never()).save(any());
    verify(movements, never()).save(any());
    verify(operations, never()).saveAndFlush(any());
  }

  @Test
  void newIngredientIsCreatedWithNormalizedNameUnitAndCategory() {
    assertTrue(service.applyPurchase(user, "op-1", List.of(new PurchaseLine("  Kırmızı Biber ", "fresh produce", new BigDecimal("3"), "ADET"))));
    ArgumentCaptor<PantryItem> saved = ArgumentCaptor.forClass(PantryItem.class);
    verify(items).save(saved.capture());
    assertEquals("Kırmızı Biber", saved.getValue().getName());
    assertEquals("kirmizi biber", saved.getValue().getNormalizedName());
    assertEquals("FRESH_PRODUCE", saved.getValue().getCategory());
    assertEquals("piece", saved.getValue().getUnit());
    verify(operations).saveAndFlush(any(PantryOperation.class));
    verify(movements).save(argThat(m -> m.getOperationKey().equals("op-1") && m.getMovementType().equals("ADD")));
  }

  @ParameterizedTest(name = "{1} {2} into {3} {4} -> {5}")
  @CsvSource({
    "domates, 500, g, 1, kg, 1.500",
    "domates, 2, kg, 300, g, 2300",
    "sut, 250, ml, 1, l, 1.250",
    "sut, 1, l, 200, ml, 1200",
    "yumurta, 6, adet, 2, piece, 8",
  })
  void compatibleUnitsAreConvertedIntoTheExistingItemsUnit(String name, String bought, String boughtUnit,
      String have, String haveUnit, String expected) {
    PantryItem item = existing(name, name, have, haveUnit);
    service.applyPurchase(user, "op", List.of(new PurchaseLine(name, null, new BigDecimal(bought), boughtUnit)));
    assertEquals(0, new BigDecimal(expected).compareTo(item.getQuantity()), item.getQuantity().toPlainString());
  }

  @Test
  void incompatibleUnitCreatesASeparateItemInsteadOfCorruptingStock() {
    PantryItem grams = existing("Un", "un", "500", "g");
    service.applyPurchase(user, "op", List.of(new PurchaseLine("Un", null, new BigDecimal("2"), "paket")));
    assertEquals(0, new BigDecimal("500").compareTo(grams.getQuantity()));
    verify(items).save(argThat(i -> i != grams && i.getUnit().equals("paket")));
  }

  @Test
  void nonPositiveQuantitiesAndEmptyPurchasesAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> service.applyPurchase(user, "a", List.of()));
    assertThrows(IllegalArgumentException.class, () -> service.applyPurchase(user, "b", null));
    assertThrows(IllegalArgumentException.class,
        () -> service.applyPurchase(user, "c", List.of(new PurchaseLine("Su", null, BigDecimal.ZERO, "l"))));
    assertThrows(IllegalArgumentException.class,
        () -> service.applyPurchase(user, "d", List.of(new PurchaseLine("Su", null, null, "l"))));
  }
}
