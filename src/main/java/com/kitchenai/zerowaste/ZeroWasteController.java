package com.kitchenai.zerowaste;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/zero-waste")
public class ZeroWasteController {
  private final ZeroWasteService service;
  public ZeroWasteController(ZeroWasteService service) { this.service = service; }

  public record PreviewRequest(String catalogRecipeId, UUID recipeId,
      @NotNull @Min(1) @Max(30) Integer servings) {}
  public record ConfirmRequest(@NotBlank @Size(max=160) String ingredientName,
      @NotNull @DecimalMin("0.001") @Digits(integer=9, fraction=3) BigDecimal quantity,
      @NotBlank @Size(max=20) String unit, String catalogRecipeId, UUID recipeId,
      UUID cookingSessionId, UUID journalEntryId, LocalDate useByDate,
      @NotBlank @Size(max=100) String operationKey) {}
  public record CorrectRequest(@NotNull @DecimalMin("0.001") @Digits(integer=9, fraction=3) BigDecimal quantity,
      @NotBlank @Size(max=20) String unit, LocalDate useByDate,
      @NotBlank @Size(max=100) String operationKey) {}
  public record ConsumeRequest(@NotNull @DecimalMin("0.001") @Digits(integer=9, fraction=3) BigDecimal quantity,
      @NotBlank @Size(max=100) String operationKey) {}
  public record OperationRequest(@NotBlank @Size(max=100) String operationKey) {}

  @PostMapping("/preview")
  public ZeroWasteService.Preview preview(@Valid @RequestBody PreviewRequest request,
      @AuthenticationPrincipal UUID userId) {
    return service.preview(request.catalogRecipeId(), request.recipeId(), request.servings(), userId);
  }

  @PostMapping("/remainders")
  public ZeroWasteService.RemainderView confirm(@Valid @RequestBody ConfirmRequest request,
      @AuthenticationPrincipal UUID userId) {
    return service.confirm(userId, request.ingredientName(), request.quantity(), request.unit(),
        request.catalogRecipeId(), request.recipeId(), request.cookingSessionId(),
        request.journalEntryId(), request.useByDate(), request.operationKey());
  }

  @GetMapping("/remainders")
  public List<ZeroWasteService.RemainderView> list(@AuthenticationPrincipal UUID userId) {
    return service.list(userId);
  }

  @GetMapping("/remainders/{id}")
  public ZeroWasteService.RemainderView get(@PathVariable UUID id, @AuthenticationPrincipal UUID userId) {
    return service.get(userId, id);
  }

  @PatchMapping("/remainders/{id}")
  public ZeroWasteService.RemainderView correct(@PathVariable UUID id,
      @Valid @RequestBody CorrectRequest request, @AuthenticationPrincipal UUID userId) {
    return service.correct(userId, id, request.quantity(), request.unit(), request.useByDate(), request.operationKey());
  }

  @PostMapping("/remainders/{id}/consume")
  public ZeroWasteService.RemainderView consume(@PathVariable UUID id,
      @Valid @RequestBody ConsumeRequest request, @AuthenticationPrincipal UUID userId) {
    return service.consume(userId, id, request.quantity(), request.operationKey());
  }

  @PostMapping("/remainders/{id}/transfer-to-pantry")
  public ZeroWasteService.RemainderView transfer(@PathVariable UUID id,
      @Valid @RequestBody OperationRequest request, @AuthenticationPrincipal UUID userId) {
    return service.transfer(userId, id, request.operationKey());
  }

  @PostMapping("/remainders/{id}/dismiss")
  public ZeroWasteService.RemainderView dismiss(@PathVariable UUID id,
      @Valid @RequestBody OperationRequest request, @AuthenticationPrincipal UUID userId) {
    return service.dismiss(userId, id, request.operationKey());
  }

  @GetMapping("/remainders/{id}/suggestions")
  public List<ZeroWasteService.Suggestion> suggestions(@PathVariable UUID id,
      @RequestParam(required=false) @Min(1) @Max(30) Integer servings,
      @AuthenticationPrincipal UUID userId) {
    return service.suggestions(userId, id, servings);
  }
}
