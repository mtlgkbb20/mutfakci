package com.kitchenai.cookingjournal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/cooking-journal")
public class CookingJournalController {
  private final CookingJournalService service;

  public CookingJournalController(CookingJournalService service) {
    this.service = service;
  }

  public record CreateRequest(
      @NotNull UUID cookingSessionId,
      @Min(1) @Max(30) Integer servings,
      @Min(1) @Max(5) Integer rating,
      String notes) {}

  public record UpdateRequest(
      @Min(1) @Max(30) Integer servings,
      @Min(1) @Max(5) Integer rating,
      String notes) {}

  public record EntryView(UUID id, UUID cookingSessionId, UUID recipeId, String recipeSnapshot,
      Integer servings, Integer rating, String notes, Instant cookedAt, Instant createdAt, Instant updatedAt) {
    static EntryView from(CookingJournalEntry entry) {
      return new EntryView(entry.getId(), entry.getCookingSessionId(), entry.getRecipeId(),
          entry.getRecipeSnapshotJson(), entry.getServings(), entry.getRating(), entry.getNotes(),
          entry.getCookedAt(), entry.getCreatedAt(), entry.getUpdatedAt());
    }
  }

  @PostMapping
  @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
  public EntryView create(@Valid @RequestBody CreateRequest request,
      @AuthenticationPrincipal UUID userId) {
    return EntryView.from(service.create(userId, new CookingJournalService.CreateRequest(
        request.cookingSessionId(), request.servings(), request.rating(), request.notes())));
  }

  @GetMapping
  public List<EntryView> list(@AuthenticationPrincipal UUID userId) {
    return service.list(userId).stream().map(EntryView::from).toList();
  }

  @GetMapping("/{entryId}")
  public EntryView get(@PathVariable UUID entryId, @AuthenticationPrincipal UUID userId) {
    return EntryView.from(service.get(userId, entryId));
  }

  @GetMapping("/recipes/{recipeId}/attempts")
  public List<EntryView> attempts(@PathVariable UUID recipeId, @AuthenticationPrincipal UUID userId) {
    return service.attempts(userId, recipeId).stream().map(EntryView::from).toList();
  }

  @PatchMapping("/{entryId}")
  public EntryView update(@PathVariable UUID entryId, @Valid @RequestBody UpdateRequest request,
      @AuthenticationPrincipal UUID userId) {
    return EntryView.from(service.update(userId, entryId,
        new CookingJournalService.UpdateRequest(request.servings(), request.rating(), request.notes())));
  }
}
