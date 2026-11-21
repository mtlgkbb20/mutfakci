package com.kitchenai.pantry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.kitchenai.common.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;

class PantryRemainderTransferServiceTest {
  private final PantryItemRepository items = mock(PantryItemRepository.class);
  private final PantryMovementRepository movements = mock(PantryMovementRepository.class);
  private final PantryOperationRepository operations = mock(PantryOperationRepository.class);
  private final PantryRemainderTransferService service = new PantryRemainderTransferService(items, movements, operations);
  private final UUID user = UUID.randomUUID();
  private final LocalDate useBy = LocalDate.now().plusDays(3);

  @BeforeEach
  void setUp() {
    when(items.save(any(PantryItem.class))).thenAnswer(inv -> inv.getArgument(0));
    when(items.lockFirstByUserAndNormalizedName(any(), any())).thenReturn(Optional.empty());
  }

  @Test
  void activeStockExcludesExpiredItemsButKeepsTodayAndUndated() {
    var expired = new PantryItem(user, "A", "a", "X", BigDecimal.ONE, "g", LocalDate.now().minusDays(1));
    var today = new PantryItem(user, "B", "b", "X", BigDecimal.ONE, "g", LocalDate.now());
    var undated = new PantryItem(user, "C", "c", "X", BigDecimal.ONE, "g", null);
    when(items.findByUserIdAndArchivedFalseOrderByCategoryAscNameAsc(user)).thenReturn(List.of(expired, today, undated));
    assertEquals(List.of("b", "c"), service.activeStock(user).stream().map(PantryRemainderTransferService.PantryStockLine::normalizedName).toList());
  }

  @Test
  void transferCreatesANewItemWithTheUseByDate() {
    UUID id = service.transfer(user, "Yumurta akı", "yumurta aki", new BigDecimal("2"), "adet", useBy, "op-1");
    verify(items).save(argThat(i -> i.getId().equals(id) && useBy.equals(i.getExpiryDate()) && i.getUnit().equals("adet")));
    verify(operations).save(any(PantryOperation.class));
    verify(movements).save(argThat(m -> m.getPantryItemId().equals(id) && m.getOperationKey().equals("op-1")));
  }

  @Test
  void transferIntoExistingItemConvertsUnitsAndBackfillsMissingExpiry() {
    PantryItem existing = new PantryItem(user, "Süt", "sut", "DAIRY", new BigDecimal("1"), "l", null);
    when(items.lockFirstByUserAndNormalizedName(user, "sut")).thenReturn(Optional.of(existing));
    UUID id = service.transfer(user, "Süt", "sut", new BigDecimal("250"), "ml", useBy, "op-2");
    assertEquals(existing.getId(), id);
    assertEquals(0, new BigDecimal("1.250").compareTo(existing.getQuantity()));
    assertEquals(useBy, existing.getExpiryDate());
    verify(movements).save(argThat(m -> m.getQuantityDelta().compareTo(new BigDecimal("0.250")) == 0 && m.getUnit().equals("l")));
  }

  @Test
  void existingExpiryIsNotOverwritten() {
    LocalDate earlier = LocalDate.now().plusDays(1);
    PantryItem existing = new PantryItem(user, "Süt", "sut", "DAIRY", new BigDecimal("1"), "l", earlier);
    when(items.lockFirstByUserAndNormalizedName(user, "sut")).thenReturn(Optional.of(existing));
    service.transfer(user, "Süt", "sut", new BigDecimal("1"), "l", useBy, "op-3");
    assertEquals(earlier, existing.getExpiryDate());
  }

  @Test
  void incompatibleUnitsAre400AndNothingIsWritten() {
    PantryItem existing = new PantryItem(user, "Un", "un", "X", new BigDecimal("1"), "kg", null);
    when(items.lockFirstByUserAndNormalizedName(user, "un")).thenReturn(Optional.of(existing));
    ApiException error = assertThrows(ApiException.class,
        () -> service.transfer(user, "Un", "un", new BigDecimal("2"), "adet", useBy, "op-4"));
    assertEquals(400, error.status.value());
    verify(operations, never()).save(any());
    verify(movements, never()).save(any());
  }

  @Test
  void repeatedOperationReturnsTheOriginalItemWithoutAddingStockAgain() {
    UUID original = UUID.randomUUID();
    when(operations.existsByUserIdAndOperationKey(user, "op-5")).thenReturn(true);
    when(movements.findFirstByUserIdAndOperationKey(user, "op-5"))
        .thenReturn(Optional.of(new PantryMovement(user, original, "ADD", BigDecimal.ONE, "g", "r", "op-5")));
    assertEquals(original, service.transfer(user, "Un", "un", BigDecimal.ONE, "g", null, "op-5"));
    verify(items, never()).save(any());
    verify(items, never()).lockFirstByUserAndNormalizedName(any(), any());
  }

  @Test
  void repeatedOperationWithoutMovementIs409() {
    when(operations.existsByUserIdAndOperationKey(user, "op-6")).thenReturn(true);
    when(movements.findFirstByUserIdAndOperationKey(user, "op-6")).thenReturn(Optional.empty());
    assertEquals(409, assertThrows(ApiException.class,
        () -> service.transfer(user, "Un", "un", BigDecimal.ONE, "g", null, "op-6")).status.value());
  }
}
