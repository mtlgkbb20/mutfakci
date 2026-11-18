package com.kitchenai.shopping;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static com.kitchenai.shopping.ShoppingListContracts.*;

@RestController
@RequestMapping("/api/v1/shopping-lists")
public class ShoppingListController {
  private final ShoppingListService service;
  public ShoppingListController(ShoppingListService service) { this.service = service; }

  @PostMapping
  public ListView create(@Valid @RequestBody CreateRequest request, @AuthenticationPrincipal UUID userId) {
    return service.create(request, userId);
  }
  @GetMapping
  public List<ListView> list(@AuthenticationPrincipal UUID userId) { return service.list(userId); }
  @GetMapping("/{listId}")
  public ListView get(@PathVariable UUID listId, @AuthenticationPrincipal UUID userId) {
    return service.get(listId, userId);
  }
  @PatchMapping("/{listId}")
  public ListView rename(@PathVariable UUID listId, @Valid @RequestBody UpdateListRequest request,
      @AuthenticationPrincipal UUID userId) { return service.rename(listId, request, userId); }
  @PostMapping("/{listId}/items")
  public ListView addItem(@PathVariable UUID listId, @Valid @RequestBody AddItemRequest request,
      @AuthenticationPrincipal UUID userId) { return service.addItem(listId, request, userId); }
  @PatchMapping("/{listId}/items/{itemId}")
  public ListView updateItem(@PathVariable UUID listId, @PathVariable UUID itemId,
      @Valid @RequestBody UpdateItemRequest request, @AuthenticationPrincipal UUID userId) {
    return service.updateItem(listId, itemId, request, userId);
  }
  @DeleteMapping("/{listId}/items/{itemId}")
  public ListView removeItem(@PathVariable UUID listId, @PathVariable UUID itemId,
      @AuthenticationPrincipal UUID userId) { return service.removeItem(listId, itemId, userId); }
  @PostMapping("/{listId}/complete")
  public CompletionView complete(@PathVariable UUID listId, @Valid @RequestBody CompleteRequest request,
      @AuthenticationPrincipal UUID userId) { return service.complete(listId, request, userId); }
}
