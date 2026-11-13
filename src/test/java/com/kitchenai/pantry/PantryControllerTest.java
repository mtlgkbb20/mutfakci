package com.kitchenai.pantry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.kitchenai.recipe.RecipeCatalogService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PantryControllerTest {
  private PantryItemRepository items;
  private PantryMovementRepository movements;
  private PantryOperationRepository operations;
  private RecipeCatalogService catalog;
  private PantryController controller;
  private final UUID userId=UUID.randomUUID();

  @BeforeEach void setUp(){
    items=mock(PantryItemRepository.class); movements=mock(PantryMovementRepository.class); operations=mock(PantryOperationRepository.class); catalog=mock(RecipeCatalogService.class);
    controller=new PantryController(items,movements,operations,catalog);
    when(items.save(any(PantryItem.class))).thenAnswer(inv->inv.getArgument(0));
    when(movements.save(any(PantryMovement.class))).thenAnswer(inv->inv.getArgument(0));
  }

  @Test void createAddsInitialMovementAndReturnsOwnedItem(){
    var result=controller.create(new PantryController.ItemRequest("Yumurta",new BigDecimal("6"),"adet","DAIRY", LocalDate.now().plusDays(10)),userId);
    assertEquals("Yumurta",result.name());
    assertEquals(new BigDecimal("6"),result.quantity());
    verify(movements).save(argThat(m->m.getMovementType().equals("ADD")&&m.getQuantityDelta().compareTo(new BigDecimal("6"))==0));
  }

  @Test void expiryListIncludesExpiredAndUpcomingItems(){
    PantryItem expired=new PantryItem(userId,"Süt","sut","DAIRY",BigDecimal.ONE,"l",LocalDate.now().minusDays(1));
    PantryItem upcoming=new PantryItem(userId,"Yumurta","yumurta","DAIRY",BigDecimal.ONE,"piece",LocalDate.now().plusDays(3));
    when(items.findByUserIdAndArchivedFalseAndExpiryDateIsNotNullAndExpiryDateLessThanEqualOrderByExpiryDateAsc(userId,LocalDate.now().plusDays(7))).thenReturn(List.of(expired,upcoming));
    var result=controller.expiring(7,userId);
    assertEquals(2,result.size());
    assertTrue(result.get(0).expired());
    assertTrue(result.get(1).expiringSoon());
  }

  @Test void duplicateConsumptionOperationDoesNotLockOrDeductAgain(){
    when(operations.existsByUserIdAndOperationKey(userId,"operation-1")).thenReturn(true);
    var result=controller.consume(new PantryController.ConsumeRequest("operation-1",List.of(new PantryController.ConsumptionLine(UUID.randomUUID(),BigDecimal.ONE,"piece"))),userId);
    assertEquals("ALREADY_APPLIED",result.get("status"));
    verify(items,never()).lockOwned(any(),any());
    verify(movements,never()).save(any());
  }

  @Test void consumptionDeductsOnlyAfterSufficientStockValidation(){
    PantryItem item=new PantryItem(userId,"Yumurta","yumurta","DAIRY",new BigDecimal("5"),"piece",null);
    UUID itemId=item.getId();
    when(operations.existsByUserIdAndOperationKey(userId,"operation-2")).thenReturn(false);
    when(items.lockOwned(itemId,userId)).thenReturn(Optional.of(item));
    var result=controller.consume(new PantryController.ConsumeRequest("operation-2",List.of(new PantryController.ConsumptionLine(itemId,new BigDecimal("2"),"adet"))),userId);
    assertEquals("APPLIED",result.get("status"));
    assertEquals(new BigDecimal("3"),item.getQuantity());
    verify(operations).save(argThat(o->o.getOperationKey().equals("operation-2")));
    verify(movements).save(argThat(m->m.getMovementType().equals("CONSUME")&&m.getQuantityDelta().compareTo(new BigDecimal("-2"))==0));
  }

  @Test void insufficientStockRejectsWholeConsumption(){
    PantryItem item=new PantryItem(userId,"Yumurta","yumurta","DAIRY",BigDecimal.ONE,"piece",null);
    UUID itemId=item.getId();
    when(operations.existsByUserIdAndOperationKey(userId,"operation-3")).thenReturn(false);
    when(items.lockOwned(itemId,userId)).thenReturn(Optional.of(item));
    assertThrows(RuntimeException.class,()->controller.consume(new PantryController.ConsumeRequest("operation-3",List.of(new PantryController.ConsumptionLine(itemId,new BigDecimal("2"),"piece"))),userId));
    assertEquals(BigDecimal.ONE,item.getQuantity());
    verify(movements,never()).save(any());
    verify(operations,never()).save(any());
  }
}
